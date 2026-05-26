"""
drugs 테이블 47K → drug_master 1:1 UPSERT 적재 스크립트

사용:
    python scripts/build_drug_master_from_drugs.py

환경변수 (.env):
    POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
"""
from __future__ import annotations

import os
import re
import sys
from typing import Any

import psycopg
from dotenv import load_dotenv

load_dotenv()

_DOSE_REGEX = re.compile(
    r"(?P<amount>\d+(?:\.\d+)?)\s*(?P<unit>mg|mcg|µg|g|ml|IU|밀리그?[램람]|마이크로그램)",
    re.IGNORECASE,
)

BATCH_SIZE = 500


def _dsn() -> str:
    return (
        f"host={os.getenv('POSTGRES_HOST', 'localhost')} "
        f"port={os.getenv('POSTGRES_PORT', '5433')} "
        f"dbname={os.getenv('POSTGRES_DB', 'pillmate')} "
        f"user={os.getenv('POSTGRES_USER', 'pillmate')} "
        f"password={os.getenv('POSTGRES_PASSWORD', 'pillmate_local')}"
    )


def extract_dose_from_name(product_name: str) -> tuple[str | None, str | None]:
    """product_name 에서 용량·단위를 추출한다. 없으면 (None, None)."""
    match = _DOSE_REGEX.search(product_name)
    if match:
        return match.group("amount"), match.group("unit").lower()
    return None, None


def build_master_row(
    *,
    drug_id: int,
    kd_code: str,
    name: str,
    ingredient: str | None,
    item_image: str | None,
) -> dict[str, Any]:
    """drugs 한 행을 drug_master 행 dict 로 변환한다."""
    dose_amount, dose_unit = extract_dose_from_name(name)
    return {
        "item_seq": kd_code,
        "product_name": name,
        "ingredient_code": None,
        "ingredient_name": ingredient,
        "dose_amount": dose_amount,
        "dose_unit": dose_unit,
        "form": None,
        "company": None,
        "image_url": item_image,
        "source": "drugs",
        "legacy_drug_id": drug_id,
    }


_UPSERT_SQL = """
INSERT INTO drug_master
    (item_seq, product_name, ingredient_code, ingredient_name,
     dose_amount, dose_unit, form, company, image_url, source, synced_at, legacy_drug_id)
VALUES
    (%(item_seq)s, %(product_name)s, %(ingredient_code)s, %(ingredient_name)s,
     %(dose_amount)s, %(dose_unit)s, %(form)s, %(company)s, %(image_url)s, %(source)s,
     NOW(), %(legacy_drug_id)s)
ON CONFLICT (item_seq) DO UPDATE SET
    product_name    = EXCLUDED.product_name,
    ingredient_name = EXCLUDED.ingredient_name,
    dose_amount     = EXCLUDED.dose_amount,
    dose_unit       = EXCLUDED.dose_unit,
    image_url       = EXCLUDED.image_url,
    synced_at       = NOW(),
    legacy_drug_id  = EXCLUDED.legacy_drug_id
"""


def run(conn: psycopg.Connection) -> int:
    """drug_master UPSERT 실행. 적재된 행 수 반환."""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, kd_code, name, ingredient, item_image FROM drugs WHERE status != 'DELETED' OR status IS NULL"
        )
        rows = cur.fetchall()

    total = 0
    batch: list[dict[str, Any]] = []

    for drug_id, kd_code, name, ingredient, item_image in rows:
        if not kd_code or not name:
            continue
        batch.append(build_master_row(
            drug_id=drug_id, kd_code=kd_code,
            name=name, ingredient=ingredient, item_image=item_image,
        ))
        if len(batch) >= BATCH_SIZE:
            total += _flush(conn, batch)
            batch.clear()

    if batch:
        total += _flush(conn, batch)

    conn.commit()
    return total


def _flush(conn: psycopg.Connection, batch: list[dict]) -> int:
    with conn.cursor() as cur:
        cur.executemany(_UPSERT_SQL, batch)
    return len(batch)


def main() -> None:
    with psycopg.connect(_dsn()) as conn:
        count = run(conn)
    print(f"drug_master UPSERT 완료: {count}건")


if __name__ == "__main__":
    main()
