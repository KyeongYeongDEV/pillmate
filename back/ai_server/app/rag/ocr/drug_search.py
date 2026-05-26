from __future__ import annotations

import logging
from decimal import Decimal
from typing import Any

from app.rag.ocr.matcher import (
    IlikeDrugSearch,
    IngredientSearch,
    MatchCandidate,
    VectorDrugSearch,
)
from app.rag.pgvector_retriever import PgVectorRetriever

logger = logging.getLogger(__name__)


_ILIKE_SQL = """
SELECT kd_code, name
FROM drugs
WHERE status = 'ACTIVE'
  AND name ILIKE '%' || $1 || '%'
ORDER BY
  CASE WHEN name ILIKE $1 THEN 0
       WHEN name ILIKE $1 || '%' THEN 1
       ELSE 2 END,
  length(name)
LIMIT 1
"""


_INGREDIENT_SQL = """
SELECT kd_code, name
FROM drugs
WHERE status = 'ACTIVE'
  AND ingredient ILIKE '%' || $1 || '%'
ORDER BY length(name)
LIMIT 1
"""

_INGREDIENT_MATCH_SCORE = Decimal("0.85")


class AsyncpgIlikeSearch(IlikeDrugSearch):
    def __init__(self, pool: Any):
        self._pool = pool

    async def search(self, name: str) -> MatchCandidate | None:
        async with self._pool.acquire() as conn:
            row = await conn.fetchrow(_ILIKE_SQL, name)
        if row is None:
            return None
        return MatchCandidate(kd_code=row["kd_code"], name=row["name"], score=Decimal("1.0"))


class AsyncpgIngredientSearch(IngredientSearch):
    def __init__(self, pool: Any):
        self._pool = pool

    async def search(self, ingredient: str) -> MatchCandidate | None:
        async with self._pool.acquire() as conn:
            row = await conn.fetchrow(_INGREDIENT_SQL, ingredient)
        if row is None:
            return None
        return MatchCandidate(
            kd_code=row["kd_code"], name=row["name"], score=_INGREDIENT_MATCH_SCORE
        )


class PgVectorDrugSearch(VectorDrugSearch):
    def __init__(self, retriever: PgVectorRetriever):
        self._retriever = retriever

    async def search(self, name: str) -> MatchCandidate | None:
        results = await self._retriever.search(name, top_k=1)
        if not results:
            return None
        top = results[0]
        score = Decimal(str(getattr(top, "score", "0"))) if hasattr(top, "score") else Decimal("0.6")
        return MatchCandidate(kd_code=top.kd_code, name=top.name, score=score)
