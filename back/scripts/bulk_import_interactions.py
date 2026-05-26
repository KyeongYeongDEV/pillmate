"""DUR 병용금기(DRUG_DRUG) 적재.

API: DURPrdlstInfoService03/getUsjntTabooInfoList03
대상: drug_interactions — UPSERT (ON CONFLICT drug_code_a, drug_code_b, type DO NOTHING).
DB safety: DELETE/TRUNCATE 절대 금지 — UPSERT 만.

사용:
    python scripts/bulk_import_interactions.py --all
    python scripts/bulk_import_interactions.py --resume
    python scripts/bulk_import_interactions.py --limit 500
    python scripts/bulk_import_interactions.py --dry-run --max-pages 1
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import httpx
import psycopg
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s :: %(message)s",
)
logger = logging.getLogger("bulk_import_interactions")

CHECKPOINT_PATH = ROOT / ".interactions_checkpoint.json"
BASE_URL = "https://apis.data.go.kr/1471000"
DUR_PATH = "/DURPrdlstInfoService03/getUsjntTabooInfoList03"
PAGE_SIZE = 100
RATE_LIMIT_SLEEP = 0.2  # 5 RPS
SOURCE = "식품의약품안전처"
TYPE = "DRUG_DRUG"
TIMEOUT = httpx.Timeout(connect=10.0, read=30.0, write=10.0, pool=10.0)

# PROHBT_CONTENT 키워드 → severity
_CRITICAL_KW = ["횡문근융해증", "사망", "금기", "병용금기", "심정지", "치명", "절대금기"]
_HIGH_KW = ["위험", "주의", "중증", "쇼크", "발작", "이상반응", "심각"]

UPSERT_SQL = """
INSERT INTO drug_interactions
    (drug_code_a, drug_code_b, type, severity, description, source, synced_at)
VALUES (%s, %s, %s, %s, %s, %s, %s)
ON CONFLICT (drug_code_a, drug_code_b, type) DO NOTHING
"""


def map_severity(content: str | None) -> str:
    text = (content or "").strip()
    for kw in _CRITICAL_KW:
        if kw in text:
            return "CRITICAL"
    for kw in _HIGH_KW:
        if kw in text:
            return "HIGH"
    return "MEDIUM"


def _dsn() -> str:
    return (
        f"host={os.environ.get('POSTGRES_HOST', 'localhost')} "
        f"port={os.environ.get('POSTGRES_PORT', '5433')} "
        f"dbname={os.environ.get('POSTGRES_DB', 'pillmate')} "
        f"user={os.environ.get('POSTGRES_USER', 'pillmate')} "
        f"password={os.environ.get('POSTGRES_PASSWORD', 'pillmate_local')}"
    )


def fetch_page(api_key: str, page: int) -> tuple[list[dict[str, Any]], int]:
    params = {"serviceKey": api_key, "type": "json", "pageNo": page, "numOfRows": PAGE_SIZE}
    delays = [1.0, 2.0, 4.0]
    last_exc: Exception | None = None
    for attempt in range(len(delays) + 1):
        try:
            with httpx.Client(timeout=TIMEOUT) as client:
                resp = client.get(BASE_URL + DUR_PATH, params=params)
            if resp.status_code >= 500:
                raise httpx.HTTPStatusError(f"5xx {resp.status_code}", request=resp.request, response=resp)
            data = resp.json()
            body = data.get("body") or data.get("response", {}).get("body") or {}
            items = body.get("items") or []
            if isinstance(items, dict):
                items = [items]
            return items, int(body.get("totalCount") or 0)
        except (httpx.TimeoutException, httpx.NetworkError, httpx.HTTPStatusError) as exc:
            last_exc = exc
            if attempt >= len(delays):
                break
            time.sleep(delays[attempt])
    raise RuntimeError(f"retries exhausted: {last_exc}")


def to_row(item: dict[str, Any]) -> tuple | None:
    code_a = (item.get("ITEM_SEQ") or "").strip()
    code_b = (item.get("MIXTURE_ITEM_SEQ") or "").strip()
    if not code_a or not code_b:
        return None
    description = (item.get("PROHBT_CONTENT") or "").strip() or "병용금기"
    return (code_a, code_b, TYPE, map_severity(description), description, SOURCE,
            datetime.now(timezone.utc))


def load_checkpoint() -> dict[str, Any]:
    if CHECKPOINT_PATH.exists():
        return json.loads(CHECKPOINT_PATH.read_text())
    return {"last_page": 0, "done_count": 0}


def save_checkpoint(page: int, done: int) -> None:
    CHECKPOINT_PATH.write_text(json.dumps(
        {"last_page": page, "done_count": done,
         "saved_at": datetime.now(timezone.utc).isoformat()},
        ensure_ascii=False, indent=2))


def run(args: argparse.Namespace) -> int:
    api_key = os.environ.get("MFDS_API_KEY", "")
    if not api_key:
        logger.error("MFDS_API_KEY 환경변수 없음")
        return 1

    checkpoint = load_checkpoint() if args.resume else {"last_page": 0, "done_count": 0}
    page = checkpoint["last_page"] + 1
    done = checkpoint["done_count"]

    dsn = _dsn()
    conn = None if args.dry_run else psycopg.connect(dsn)

    try:
        while True:
            if args.max_pages and page > checkpoint["last_page"] + args.max_pages:
                logger.info("max-pages %d 도달", args.max_pages)
                break

            items, total = fetch_page(api_key, page)
            if not items:
                logger.info("빈 응답 — 완료 (page=%d total=%d)", page, total)
                break

            rows = [r for r in (to_row(it) for it in items) if r is not None]
            total_pages = -(-total // PAGE_SIZE) if total else 1

            if args.dry_run:
                logger.info("[DRY-RUN] page=%d/%d parsed=%d/%d",
                            page, total_pages, len(rows), len(items))
                for r in rows[:3]:
                    logger.info("  sample: code_a=%s code_b=%s severity=%s", r[0], r[1], r[3])
            else:
                with conn.cursor() as cur:
                    cur.executemany(UPSERT_SQL, rows)
                conn.commit()
                done += len(rows)
                save_checkpoint(page, done)
                logger.info("page=%d/%d | upserted=%d | cumulative=%d | total=%d",
                            page, total_pages, len(rows), done, total)

            if args.limit and done >= args.limit:
                logger.info("--limit %d 도달", args.limit)
                break

            if page * PAGE_SIZE >= total > 0:
                logger.info("전체 %d페이지 완료", total_pages)
                break

            page += 1
            time.sleep(RATE_LIMIT_SLEEP)

        logger.info("완료: 총 %d건 적재", done)
    finally:
        if conn:
            conn.close()
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(description="식약처 DUR 병용금기 일괄 적재")
    parser.add_argument("--all", dest="all", action="store_true", help="전체 적재")
    parser.add_argument("--resume", action="store_true", help="체크포인트에서 이어서")
    parser.add_argument("--dry-run", action="store_true", help="DB 적재 없이 파싱만")
    parser.add_argument("--limit", type=int, default=0, help="N건 적재 후 종료")
    parser.add_argument("--max-pages", type=int, default=0, help="N페이지까지만")
    args = parser.parse_args()

    if not any([args.all, args.resume, args.dry_run, args.limit, args.max_pages]):
        parser.print_help()
        sys.exit(1)

    sys.exit(run(args))


if __name__ == "__main__":
    main()
