from __future__ import annotations

from typing import Any

import pytest

from app.rag.pgvector_retriever import MIN_SCORE, PgVectorRetriever
from app.rag.retriever import RetrievedDrug


class FakeEmbedder:
    def __init__(self, vector: list[float]):
        self.vector = vector
        self.calls: list[str] = []

    async def aembed_query(self, text: str) -> list[float]:
        self.calls.append(text)
        return self.vector


class FakeConnection:
    def __init__(self, rows: list[dict[str, Any]]):
        self._rows = rows
        self.executed: list[tuple[str, tuple[Any, ...]]] = []

    async def fetch(self, sql: str, *args: Any) -> list[dict[str, Any]]:
        self.executed.append((sql, args))
        return self._rows


class FakeAcquireContext:
    def __init__(self, conn: FakeConnection):
        self._conn = conn

    async def __aenter__(self) -> FakeConnection:
        return self._conn

    async def __aexit__(self, exc_type, exc, tb) -> None:
        return None


class FakePool:
    def __init__(self, conn: FakeConnection):
        self._conn = conn

    def acquire(self) -> FakeAcquireContext:
        return FakeAcquireContext(self._conn)


def _row(kd_code: str, name: str, score: float) -> dict[str, Any]:
    return {
        "kd_code": kd_code,
        "name": name,
        "efficacy": f"{name}-효능",
        "dosage": f"{name}-용법",
        "main_ingr": None,
        "score": score,
    }


async def test_pgvector_retriever_embeds_query_and_returns_top_k():
    rows = [_row("100000001", "타이레놀정500밀리그람", 0.93)]
    conn = FakeConnection(rows)
    embedder = FakeEmbedder([0.1] * 768)
    retriever = PgVectorRetriever(pool=FakePool(conn), embedder=embedder)

    result = await retriever.search("타이레놀", top_k=5)

    assert len(embedder.calls) == 1
    assert embedder.calls[0] == "타이레놀"
    assert len(result) == 1
    assert isinstance(result[0], RetrievedDrug)
    assert result[0].kd_code == "100000001"


async def test_pgvector_retriever_filters_below_min_score():
    rows = [
        _row("100000001", "고점수약", 0.9),
        _row("100000002", "임계치미달", MIN_SCORE - 0.01),
    ]
    retriever = PgVectorRetriever(
        pool=FakePool(FakeConnection(rows)),
        embedder=FakeEmbedder([0.0] * 768),
    )

    result = await retriever.search("질문", top_k=5)

    assert [d.kd_code for d in result] == ["100000001"]


async def test_pgvector_retriever_returns_empty_when_no_rows():
    retriever = PgVectorRetriever(
        pool=FakePool(FakeConnection([])),
        embedder=FakeEmbedder([0.0] * 768),
    )

    result = await retriever.search("아무것도 없음", top_k=5)

    assert result == []


async def test_pgvector_retriever_passes_limit_argument():
    conn = FakeConnection([])
    retriever = PgVectorRetriever(pool=FakePool(conn), embedder=FakeEmbedder([0.0] * 768))

    await retriever.search("타이레놀", top_k=7)

    sql, args = conn.executed[0]
    assert "drug_embeddings" in sql
    assert "vector_cosine_ops" in sql or "<=>" in sql
    assert args[-1] == 7


@pytest.mark.integration
async def test_pgvector_retriever_returns_top_k_for_korean_query():
    """실제 DB + Gemini 임베딩 1회 호출.

    CTO 가 별도 실행: pytest ai_server/tests/ -v -m integration
    """
    import asyncpg
    from app.core.config import get_settings
    from app.rag.pgvector_retriever import PgVectorRetriever, GeminiEmbeddingAdapter

    settings = get_settings()
    dsn = (
        f"postgresql://{settings.postgres_user}:{settings.postgres_password}"
        f"@{settings.postgres_host}:{settings.postgres_port}/{settings.postgres_db}"
    )
    pool = await asyncpg.create_pool(dsn=dsn, min_size=1, max_size=2)
    try:
        retriever = PgVectorRetriever(
            pool=pool,
            embedder=GeminiEmbeddingAdapter(api_key=settings.gemini_api_key),
        )
        result = await retriever.search("타이레놀 효능", top_k=5)

        assert len(result) >= 1
        assert all(r.kd_code for r in result)
    finally:
        await pool.close()
