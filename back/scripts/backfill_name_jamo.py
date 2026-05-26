"""drugs.name_jamo 자모 백필.

jamotools.split_syllables(name) → name_jamo UPDATE.
IDEMPOTENT: WHERE name_jamo IS NULL. 배치 1000건.
"""
from __future__ import annotations

import logging
import os
import sys
import time
from pathlib import Path

import psycopg
import jamotools
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s :: %(message)s",
)
logger = logging.getLogger("backfill_name_jamo")

BATCH_SIZE = 1000

SELECT_SQL = """
SELECT id, name
FROM drugs
WHERE name_jamo IS NULL
ORDER BY id
LIMIT %s
"""

UPDATE_SQL = """
UPDATE drugs
SET name_jamo = %s
WHERE id = %s
"""


def to_name_jamo(name: str) -> str:
    return jamotools.split_syllables(name)


def main() -> int:
    load_dotenv()
    dsn = (
        f"host={os.environ.get('POSTGRES_HOST', 'localhost')} "
        f"port={os.environ.get('POSTGRES_PORT', '5433')} "
        f"user={os.environ.get('POSTGRES_USER', 'pillmate')} "
        f"password={os.environ.get('POSTGRES_PASSWORD', 'pillmate_local')} "
        f"dbname={os.environ.get('POSTGRES_DB', 'pillmate')}"
    )
    total_updated = 0
    batch_num = 0
    start = time.time()

    with psycopg.connect(dsn) as conn, conn.cursor() as cur:
        while True:
            cur.execute(SELECT_SQL, (BATCH_SIZE,))
            rows = cur.fetchall()
            if not rows:
                break
            batch = [(to_name_jamo(name), drug_id) for drug_id, name in rows]
            cur.executemany(UPDATE_SQL, batch)
            conn.commit()
            total_updated += len(batch)
            batch_num += 1
            logger.info("batch=%d updated=%d running=%d", batch_num, len(batch), total_updated)

    elapsed = time.time() - start
    logger.info("DONE total_updated=%d elapsed=%.1fs", total_updated, elapsed)
    return 0


if __name__ == "__main__":
    sys.exit(main())
