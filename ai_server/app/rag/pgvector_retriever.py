from __future__ import annotations

import logging
from typing import Any, Protocol

from app.rag.retriever import DrugRetriever, RetrievedDrug

logger = logging.getLogger(__name__)

EMBEDDING_DIM = 768
MIN_SCORE = 0.55
EMBEDDING_MODEL = "models/text-embedding-004"

_TOP_K_SQL = """
SELECT d.kd_code AS kd_code,
       d.name AS name,
       d.efficacy AS efficacy,
       d.dosage AS dosage,
       d.main_ingr AS main_ingr,
       1 - (e.embedding <=> $1::vector) AS score
FROM drug_embeddings e
JOIN drugs d ON d.id = e.drug_id
WHERE d.status = 'ACTIVE'
ORDER BY e.embedding <=> $1::vector
LIMIT $2
"""


class QueryEmbedder(Protocol):
    async def aembed_query(self, text: str) -> list[float]:
        ...


class GeminiEmbeddingAdapter(QueryEmbedder):
    def __init__(self, api_key: str, model: str = EMBEDDING_MODEL):
        from langchain_google_genai import GoogleGenerativeAIEmbeddings

        self._client = GoogleGenerativeAIEmbeddings(model=model, google_api_key=api_key)

    async def aembed_query(self, text: str) -> list[float]:
        return await self._client.aembed_query(text)


class PgVectorRetriever(DrugRetriever):
    def __init__(self, pool: Any, embedder: QueryEmbedder, min_score: float = MIN_SCORE):
        self._pool = pool
        self._embedder = embedder
        self._min_score = min_score

    async def search(self, query: str, top_k: int) -> list[RetrievedDrug]:
        vector = await self._embedder.aembed_query(query)
        rows = await self._fetch_top_k(vector, top_k)
        return [_row_to_drug(row) for row in rows if _passes_threshold(row, self._min_score)]

    async def _fetch_top_k(self, vector: list[float], top_k: int) -> list[Any]:
        vector_literal = _format_vector(vector)
        async with self._pool.acquire() as conn:
            return await conn.fetch(_TOP_K_SQL, vector_literal, top_k)


def _passes_threshold(row: Any, min_score: float) -> bool:
    return float(row["score"]) >= min_score


def _row_to_drug(row: Any) -> RetrievedDrug:
    return RetrievedDrug(
        kd_code=row["kd_code"],
        name=row["name"],
        efficacy=row["efficacy"],
        dosage=row["dosage"],
        main_ingr=row["main_ingr"],
    )


def _format_vector(vector: list[float]) -> str:
    return "[" + ",".join(f"{v:.8f}" for v in vector) + "]"
