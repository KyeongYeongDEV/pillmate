"""
drug_master → drug_alias (product 소스) UPSERT 적재 스크립트

alias 종류:
  1. product_name 그대로  (confidence=100)
  2. 용량 제거 변형       (confidence=85, 이름이 달라질 경우에만)

사용:
    python scripts/build_product_aliases.py

환경변수 (.env):
    POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
"""
from __future__ import annotations

import os
import re
import sys
from typing import Any

import jamotools
import psycopg
from dotenv import load_dotenv

load_dotenv()

_DOSE_REGEX = re.compile(
    r"\s*\d+(?:\.\d+)?\s*(?:mg|mcg|µg|g|ml|IU|밀리그?[램람]|마이크로그램)",
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


def _strip_dose(name: str) -> str:
    """product_name 에서 용량 표기를 제거한다."""
    return _DOSE_REGEX.sub("", name).strip()


def build_aliases_for_product(product_name: str, item_seq: str) -> list[dict[str, Any]]:
    """product_name 하나에서 생성할 alias 목록을 반환한다."""
    aliases: list[dict[str, Any]] = []

    def _make(alias_text: str, confidence: int) -> dict[str, Any]:
        return {
            "alias": alias_text,
            "alias_jamo": jamotools.split_syllables(alias_text),
            "item_seq": item_seq,
            "source": "product",
            "confidence": confidence,
        }

    aliases.append(_make(product_name, 100))

    dose_stripped = _strip_dose(product_name)
    if dose_stripped and dose_stripped != product_name:
        aliases.append(_make(dose_stripped, 85))

    return aliases


_UPSERT_SQL = """
INSERT INTO drug_alias (alias, alias_jamo, item_seq, source, confidence, created_at)
VALUES (%(alias)s, %(alias_jamo)s, %(item_seq)s, %(source)s, %(confidence)s, NOW())
ON CONFLICT (alias, item_seq) DO UPDATE SET
    alias_jamo = EXCLUDED.alias_jamo,
    confidence = GREATEST(drug_alias.confidence, EXCLUDED.confidence)
"""


def run(conn: psycopg.Connection) -> int:
    """drug_alias UPSERT 실행. 적재된 행 수 반환."""
    with conn.cursor() as cur:
        cur.execute("SELECT item_seq, product_name FROM drug_master")
        rows = cur.fetchall()

    total = 0
    batch: list[dict[str, Any]] = []

    for item_seq, product_name in rows:
        if not product_name:
            continue
        for alias_row in build_aliases_for_product(product_name, item_seq):
            batch.append(alias_row)
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
    print(f"drug_alias (product) UPSERT 완료: {count}건")


if __name__ == "__main__":
    main()
