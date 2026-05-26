from __future__ import annotations

import logging
from typing import Iterable

import asyncpg

from app.rag.retriever import DrugRetriever, RetrievedDrug

logger = logging.getLogger(__name__)


_SEARCH_SQL = """
SELECT kd_code, name, efficacy, dosage, main_ingr
FROM drugs
WHERE status = 'ACTIVE'
  AND (
        name      ILIKE '%' || $1 || '%'
     OR main_ingr ILIKE '%' || $1 || '%'
     OR ingredient ILIKE '%' || $1 || '%'
  )
ORDER BY
  CASE WHEN name ILIKE $1 THEN 0
       WHEN name ILIKE $1 || '%' THEN 1
       ELSE 2 END,
  name
LIMIT $2
"""


class AsyncpgDrugRetriever(DrugRetriever):
    def __init__(self, pool: asyncpg.Pool):
        self._pool = pool

    async def search(self, query: str, top_k: int) -> list[RetrievedDrug]:
        async with self._pool.acquire() as conn:
            rows = await conn.fetch(_SEARCH_SQL, query, top_k)
        return [_row_to_drug(row) for row in rows]


def _row_to_drug(row: Iterable) -> RetrievedDrug:
    return RetrievedDrug(
        kd_code=row["kd_code"],
        name=row["name"],
        efficacy=row["efficacy"],
        dosage=row["dosage"],
        main_ingr=row["main_ingr"],
    )


async def build_pool(dsn: str) -> asyncpg.Pool:
    logger.info("creating asyncpg pool for %s", _mask_dsn(dsn))
    return await asyncpg.create_pool(dsn=dsn, min_size=1, max_size=5)


def _mask_dsn(dsn: str) -> str:
    if "@" not in dsn:
        return dsn
    head, tail = dsn.split("@", 1)
    return f"<masked>@{tail}"
