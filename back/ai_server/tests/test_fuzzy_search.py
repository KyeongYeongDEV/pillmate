from __future__ import annotations

import jamotools
import Levenshtein
import pytest

from app.rag.ocr.fuzzy_search import (
    JAMO_DISTANCE_THRESHOLD,
    FuzzyCandidate,
    JamoFuzzyRanker,
    TrigramFuzzySearch,
    to_chosung,
)


# ── Mock asyncpg pool ──────────────────────────────────────────────────────────

class _MockRow:
    def __init__(self, **kwargs: object) -> None:
        self._data = kwargs

    def __getitem__(self, key: str) -> object:
        return self._data[key]


class _MockConn:
    def __init__(self, rows: list[_MockRow]) -> None:
        self._rows = rows

    async def fetch(self, sql: str, *args: object) -> list[_MockRow]:
        return self._rows

    async def __aenter__(self) -> "_MockConn":
        return self

    async def __aexit__(self, *args: object) -> None:
        pass


class _MockPool:
    def __init__(self, rows: list[_MockRow]) -> None:
        self._conn = _MockConn(rows)

    def acquire(self) -> "_MockConn":
        return self._conn


# ── Tests ──────────────────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_trigram_finds_candidates():
    rows = [
        _MockRow(
            kd_code="KD001",
            name="암로디핀베실산염정",
            name_jamo=jamotools.split_syllables("암로디핀베실산염정"),
            trgm_score=0.6,
        )
    ]
    searcher = TrigramFuzzySearch(_MockPool(rows))
    result = await searcher.search("암로디핀")
    assert len(result) >= 1
    assert result[0].kd_code == "KD001"


def test_jamo_distance_calculation():
    jamo_a = jamotools.split_syllables("암르디핀")
    jamo_b = jamotools.split_syllables("암로디핀")
    assert Levenshtein.distance(jamo_a, jamo_b) == 1


def test_jamo_rerank_orders_correctly():
    ranker = JamoFuzzyRanker()
    query_jamo = jamotools.split_syllables("암로디핀")
    candidates = [
        FuzzyCandidate(
            kd_code="KD002",
            name="암르디핀정",
            name_jamo=jamotools.split_syllables("암르디핀"),
            trgm_score=0.6,
        ),
        FuzzyCandidate(
            kd_code="KD001",
            name="암로디핀정",
            name_jamo=jamotools.split_syllables("암로디핀"),
            trgm_score=0.8,
        ),
    ]
    result = ranker.rerank(query_jamo, candidates)
    # KD001 "암로디핀" distance=0 → highest jamo_score → first
    assert result[0].kd_code == "KD001"


def test_chosung_extraction():
    assert to_chosung("메트포르민") == "ㅁㅌㅍㄹㅁ"


@pytest.mark.asyncio
async def test_fuzzy_handles_unknown_drug():
    searcher = TrigramFuzzySearch(_MockPool([]))
    result = await searcher.search("존재하지않는약품명XYZ")
    assert result == []


def test_fuzzy_threshold_rejects_far():
    ranker = JamoFuzzyRanker()
    query_jamo = jamotools.split_syllables("암로디핀")
    far = FuzzyCandidate(
        kd_code="KD999",
        name="리스페리돈정",
        name_jamo=jamotools.split_syllables("리스페리돈"),
        trgm_score=0.3,
    )
    result = ranker.rerank(query_jamo, [far])
    assert result == [], f"distance > {JAMO_DISTANCE_THRESHOLD} 후보는 제외돼야 함"
