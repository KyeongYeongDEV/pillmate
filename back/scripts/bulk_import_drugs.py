"""
식약처 의약품 데이터 일괄 적재 스크립트

사용:
    python scripts/bulk_import_drugs.py --all            # 전체 적재 (최초 1회)
    python scripts/bulk_import_drugs.py --resume         # 체크포인트에서 이어서
    python scripts/bulk_import_drugs.py --delta-only --since 2026-05-01

환경변수 (.env):
    MFDS_API_KEY, POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path

import httpx
import psycopg
from dotenv import load_dotenv

load_dotenv()

CHECKPOINT_FILE = Path(".mfds_bulk_checkpoint.json")
PAGE_SIZE = 100
DELAY_MS = 200

# 식약처 공공데이터 API 기본 URL
BASE_URL = "https://apis.data.go.kr/1471000"

# e약은요 API (효능효과, 용법용량, 부작용 포함)
EASY_DRUG_ENDPOINT = "/DrbEasyDrugInfoService/getDrbEasyDrugList"

def _dsn() -> str:
    return (
        f"host={os.getenv('POSTGRES_HOST', 'localhost')} "
        f"port={os.getenv('POSTGRES_PORT', '5433')} "
        f"dbname={os.getenv('POSTGRES_DB', 'pillmate')} "
        f"user={os.getenv('POSTGRES_USER', 'pillmate')} "
        f"password={os.getenv('POSTGRES_PASSWORD', 'pillmate_local')}"
    )


def load_checkpoint() -> dict:
    if CHECKPOINT_FILE.exists():
        return json.loads(CHECKPOINT_FILE.read_text())
    return {"page": 1, "total_imported": 0}


def save_checkpoint(page: int, total: int) -> None:
    CHECKPOINT_FILE.write_text(
        json.dumps({"page": page, "total_imported": total, "saved_at": datetime.now().isoformat()})
    )


def fetch_page(page: int, api_key: str, since: str | None = None) -> tuple[list[dict], int]:
    """e약은요 API 1페이지 조회. (items, totalCount) 반환"""
    params: dict = {
        "serviceKey": api_key,
        "pageNo": page,
        "numOfRows": PAGE_SIZE,
        "type": "json",
    }
    if since:
        params["lastModTs"] = since.replace("-", "")  # YYYYMMDD

    resp = httpx.get(BASE_URL + EASY_DRUG_ENDPOINT, params=params, timeout=30)
    resp.raise_for_status()

    body = resp.json().get("body", {})
    items = body.get("items", []) or []
    total_count = int(body.get("totalCount", 0))
    return items, total_count


def upsert_drugs(records: list[dict]) -> int:
    """DB upsert, 성공 건수 반환"""
    if not records:
        return 0

    rows = [
        (
            r.get("itemSeq", ""),           # kd_code
            r.get("itemName", ""),          # name
            r.get("material"),              # ingredient
            r.get("efcyQesitm"),            # efficacy
            r.get("useMethodQesitm"),       # dosage
            r.get("seQesitm"),              # side_effect
            r.get("formCodeName"),          # form
            r.get("entpName"),              # company
        )
        for r in records
        if r.get("itemSeq") and r.get("itemName")
    ]

    with psycopg.connect(_dsn()) as conn:
        with conn.cursor() as cur:
            cur.executemany(
                """
                INSERT INTO drugs
                    (kd_code, name, ingredient, efficacy, dosage, side_effect, form, company, synced_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW())
                ON CONFLICT (kd_code) DO UPDATE SET
                    name        = EXCLUDED.name,
                    ingredient  = EXCLUDED.ingredient,
                    efficacy    = EXCLUDED.efficacy,
                    dosage      = EXCLUDED.dosage,
                    side_effect = EXCLUDED.side_effect,
                    form        = EXCLUDED.form,
                    company     = EXCLUDED.company,
                    synced_at   = NOW(),
                    version     = drugs.version + 1
                """,
                rows,
            )
        conn.commit()
    return len(rows)


def main() -> int:
    parser = argparse.ArgumentParser(description="식약처 의약품 일괄 적재")
    parser.add_argument("--all", action="store_true", help="전체 적재")
    parser.add_argument("--resume", action="store_true", help="체크포인트에서 이어서")
    parser.add_argument("--delta-only", action="store_true", help="변경분만 동기화")
    parser.add_argument("--since", type=str, help="delta-only 기준 날짜 (YYYY-MM-DD)")
    args = parser.parse_args()

    api_key = os.getenv("MFDS_API_KEY", "")
    if not api_key:
        print("MFDS_API_KEY 환경변수가 없습니다. .env 파일을 확인하세요.", file=sys.stderr)
        return 1

    checkpoint = load_checkpoint() if args.resume else {"page": 1, "total_imported": 0}
    start_page = checkpoint["page"]
    total = checkpoint["total_imported"]

    since = args.since if args.delta_only else None
    print(f"식약처 의약품 데이터 적재 시작 (page {start_page}~, since={since})")

    try:
        page = start_page
        while True:
            items, total_count = fetch_page(page, api_key, since)
            if not items:
                break

            imported = upsert_drugs(items)
            total += imported
            save_checkpoint(page + 1, total)
            print(f"  page {page}: {imported}건 적재 (누적 {total}/{total_count}건)", end="\r")

            if page * PAGE_SIZE >= total_count:
                break

            page += 1
            time.sleep(DELAY_MS / 1000)

    except httpx.HTTPError as e:
        print(f"\nAPI 호출 오류: {e}", file=sys.stderr)
        return 2

    print(f"\n완료: 총 {total}건 적재")
    CHECKPOINT_FILE.unlink(missing_ok=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
