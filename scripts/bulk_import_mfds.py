"""식약처 3 API 통합 벌크 적재.

각 페이지마다 e약은요/낱알식별/제품허가를 1페이지씩 가져와 itemSeq 키로 머지하고,
drugs 테이블에 upsert 한다.

옵션
- --dry-run --max-pages 1 : 각 API 1페이지만, DB 적재 없이 첫 5건 머지 결과 + 통계 출력
- --all                    : 전체 적재
- --resume                 : 체크포인트에서 이어서
- --limit N                : N 건 적재 후 정상 종료
- --max-pages N            : N 페이지까지만
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from contextlib import closing
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from dotenv import load_dotenv

# 모듈 import (scripts 패키지 경로 보장)
ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.lib.mfds_clients import (  # noqa: E402
    MfdsClientError,
    fetch_easy,
    fetch_ident,
    fetch_permit,
)
from scripts.lib.mfds_merge import merge_drug_record  # noqa: E402

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s :: %(message)s",
)
logger = logging.getLogger("bulk_import_mfds")

CHECKPOINT_PATH = ROOT / ".mfds_bulk_checkpoint.json"
DEFAULT_PAGE_SIZE = 100
DAILY_LIMIT_PER_API = 10_000
SLEEP_BETWEEN_CALLS_SEC = 0.2

SOURCE = "식품의약품안전처"

DRUGS_COLUMNS = [
    "kd_code",
    "name",
    "ingredient",
    "efficacy",
    "dosage",
    "side_effect",
    "form",
    "company",
    "shape_class",
    "color_class",
    "line_front",
    "line_back",
    "mark_code_front",
    "mark_code_back",
    "chart",
    "item_image",
    "permit_no",
    "permit_date",
    "cancel_date",
    "etc_otc",
    "is_rare",
    "storage_method",
    "main_ingr",
    "warning",
    "precaution",
    "interaction",
    "class_name",
    "open_de",
    "update_de",
    "bizrno",
]


def _load_checkpoint() -> dict[str, Any]:
    if not CHECKPOINT_PATH.exists():
        return {}
    try:
        return json.loads(CHECKPOINT_PATH.read_text(encoding="utf-8"))
    except Exception as exc:
        logger.warning("checkpoint load failed: %s", exc)
        return {}


def _save_checkpoint(state: dict[str, Any]) -> None:
    CHECKPOINT_PATH.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def _connect_db():
    import psycopg

    return psycopg.connect(
        host=os.environ.get("POSTGRES_HOST", "localhost"),
        port=int(os.environ.get("POSTGRES_PORT", "5432")),
        dbname=os.environ.get("POSTGRES_DB", "pillmate"),
        user=os.environ.get("POSTGRES_USER", "pillmate"),
        password=os.environ.get("POSTGRES_PASSWORD", ""),
        autocommit=False,
    )


def _build_upsert_sql() -> str:
    cols = ["source", "synced_at", *DRUGS_COLUMNS]
    placeholders = ", ".join(f"%({c})s" for c in cols)
    update_set = ", ".join(
        f"{c} = EXCLUDED.{c}"
        for c in cols
        if c != "kd_code"
    )
    return (
        f"INSERT INTO drugs ({', '.join(cols)}) VALUES ({placeholders}) "
        f"ON CONFLICT (kd_code) DO UPDATE SET {update_set}, version = drugs.version + 1"
    )


def _row_to_params(row: dict[str, Any]) -> dict[str, Any]:
    params: dict[str, Any] = {c: None for c in DRUGS_COLUMNS}
    params.update({k: v for k, v in row.items() if k in params})
    # NOT NULL 컬럼 기본값
    if params.get("is_rare") is None:
        params["is_rare"] = False
    params["source"] = SOURCE
    params["synced_at"] = datetime.now(timezone.utc)
    return params


def _fetch_one_api_page(
    name: str, fn, api_key: str, page: int, num: int
) -> tuple[list[dict[str, Any]], int]:
    try:
        items, total = fn(api_key, page, num)
        logger.info("[%s] page=%d got=%d total=%d", name, page, len(items), total)
        return items, total
    except MfdsClientError as exc:
        logger.error("[%s] page=%d failed: %s", name, page, exc)
        raise


def _key_of(rec: dict[str, Any]) -> str | None:
    v = rec.get("itemSeq") or rec.get("ITEM_SEQ")
    if v is None:
        return None
    s = str(v).strip()
    return s or None


def _classify(by_seq: dict[str, dict[str, Any]]) -> dict[str, int]:
    stats = {"easy_only": 0, "ident_only": 0, "permit_only": 0, "two_of_three": 0, "all_three": 0, "total": 0}
    for bucket in by_seq.values():
        flags = (bool(bucket.get("easy")), bool(bucket.get("ident")), bool(bucket.get("permit")))
        count = sum(flags)
        if count == 3:
            stats["all_three"] += 1
        elif count == 2:
            stats["two_of_three"] += 1
        elif flags[0]:
            stats["easy_only"] += 1
        elif flags[1]:
            stats["ident_only"] += 1
        elif flags[2]:
            stats["permit_only"] += 1
        stats["total"] += 1
    return stats


def run(args: argparse.Namespace) -> int:
    load_dotenv(ROOT / ".env")
    api_key = os.environ.get("MFDS_API_KEY")
    if not api_key:
        logger.error("MFDS_API_KEY not set")
        return 2

    state = _load_checkpoint() if args.resume else {}
    page_state = {
        "easy": state.get("easy", {"page": 0, "total": None, "done": False}),
        "ident": state.get("ident", {"page": 0, "total": None, "done": False}),
        "permit": state.get("permit", {"page": 0, "total": None, "done": False}),
    }
    imported = int(state.get("imported", 0))
    call_count = {"easy": 0, "ident": 0, "permit": 0}

    page_size = args.page_size
    max_pages = args.max_pages

    conn = None
    upsert_sql = _build_upsert_sql()
    if not args.dry_run:
        conn = _connect_db()

    try:
        page_idx = 0
        first_5_printed = 0
        while True:
            page_idx += 1
            if max_pages is not None and page_idx > max_pages:
                logger.info("max-pages=%d reached, stopping", max_pages)
                break

            buckets: dict[str, dict[str, Any]] = {}

            for name, fn in [
                ("easy", fetch_easy),
                ("ident", fetch_ident),
                ("permit", fetch_permit),
            ]:
                ps = page_state[name]
                if ps["done"]:
                    continue
                if call_count[name] >= DAILY_LIMIT_PER_API:
                    logger.warning("[%s] daily limit %d reached", name, DAILY_LIMIT_PER_API)
                    ps["done"] = True
                    continue
                next_page = ps["page"] + 1
                items, total = _fetch_one_api_page(name, fn, api_key, next_page, page_size)
                call_count[name] += 1
                ps["page"] = next_page
                ps["total"] = total
                if not items or (total and next_page * page_size >= total):
                    ps["done"] = True
                for rec in items:
                    seq = _key_of(rec)
                    if not seq:
                        continue
                    buckets.setdefault(seq, {})[name] = rec
                time.sleep(SLEEP_BETWEEN_CALLS_SEC)

            if not buckets:
                logger.info("no records on iteration %d; all APIs done=%s", page_idx,
                            {n: ps["done"] for n, ps in page_state.items()})
                if all(ps["done"] for ps in page_state.values()):
                    break
                continue

            # 머지
            rows: list[dict[str, Any]] = []
            for seq, src in buckets.items():
                merged = merge_drug_record(src.get("easy"), src.get("ident"), src.get("permit"))
                # kd_code 보정 (혹시 누락이면 itemSeq 직접 사용)
                merged.setdefault("kd_code", seq)
                rows.append(merged)

            stats = _classify(buckets)
            logger.info(
                "iter=%d merged=%d easy_only=%d ident_only=%d permit_only=%d two=%d three=%d",
                page_idx, stats["total"], stats["easy_only"], stats["ident_only"],
                stats["permit_only"], stats["two_of_three"], stats["all_three"],
            )

            if args.dry_run:
                for r in rows:
                    if first_5_printed >= 5:
                        break
                    print(json.dumps(r, ensure_ascii=False, default=str, indent=2))
                    first_5_printed += 1
                print(f"[dry-run stats] {stats}")

            else:
                assert conn is not None
                with conn.cursor() as cur:
                    for r in rows:
                        params = _row_to_params(r)
                        if not params["kd_code"]:
                            continue
                        cur.execute(upsert_sql, params)
                        imported += 1
                        if args.limit and imported >= args.limit:
                            break
                conn.commit()

                state_to_save = {
                    "easy": page_state["easy"],
                    "ident": page_state["ident"],
                    "permit": page_state["permit"],
                    "imported": imported,
                }
                _save_checkpoint(state_to_save)

                if args.limit and imported >= args.limit:
                    logger.info("limit=%d reached, imported=%d", args.limit, imported)
                    break

            if all(ps["done"] for ps in page_state.values()):
                logger.info("all APIs exhausted")
                break

        if args.dry_run:
            logger.info("dry-run done. calls=%s", call_count)
        else:
            logger.info("import done. imported=%d calls=%s", imported, call_count)
        return 0
    finally:
        if conn is not None:
            conn.close()


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="식약처 3 API 통합 벌크 적재")
    p.add_argument("--dry-run", action="store_true", help="DB 적재 없이 머지 결과 stdout")
    p.add_argument("--all", dest="run_all", action="store_true", help="전체 적재")
    p.add_argument("--resume", action="store_true", help="체크포인트에서 이어서")
    p.add_argument("--limit", type=int, default=None, help="최대 적재 건수")
    p.add_argument("--max-pages", type=int, default=None, help="API 당 최대 페이지 수")
    p.add_argument("--page-size", type=int, default=DEFAULT_PAGE_SIZE, help="페이지당 numOfRows")
    args = p.parse_args(argv)
    if args.dry_run and args.max_pages is None:
        args.max_pages = 1
    return args


if __name__ == "__main__":
    sys.exit(run(parse_args()))
