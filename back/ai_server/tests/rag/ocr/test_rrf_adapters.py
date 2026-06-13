"""
TDD RED — RrfMatcher 운영 어댑터 단위 테스트
- ExactIlikeAdapter: 함량 포함 prefix 검색 (exact_fast path)
- IlikeMultiAdapter: 복수 ILIKE 후보 반환 (MultiRetrieverPort)
- TrigramMultiAdapter: trigram+jamo 후보 반환 (MultiRetrieverPort)
- VectorMultiAdapter: vector 후보 반환 (MultiRetrieverPort)
- _dose_in_name: 함량 문자열 매칭 유틸
"""
from __future__ import annotations

from decimal import Decimal
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import jamotools
import pytest

from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.rrf import Candidate
from app.rag.ocr.rrf_adapters import (
    ExactIlikeAdapter,
    IlikeMultiAdapter,
    TrigramMultiAdapter,
    VectorMultiAdapter,
    _dose_in_name,
)


def _parsed(
    name: str,
    dose_amount: Decimal | None = None,
    dose_unit: str | None = None,
    form: str | None = None,
) -> ParsedItem:
    return ParsedItem(
        raw=name,
        name=name,
        dose_amount=dose_amount,
        dose_unit=dose_unit,
        form=form,
        name_jamo=jamotools.split_syllables(name),
        is_valid=True,
        validation_errors=[],
    )


def _row(kd_code: str, name: str, name_jamo: str | None = None) -> dict:
    return {"kd_code": kd_code, "name": name, "name_jamo": name_jamo or jamotools.split_syllables(name)}


# ── _dose_in_name ──────────────────────────────────────────────────

class TestDoseInName:
    def test_mg_in_name_matches(self):
        parsed = _parsed("타이레놀정", dose_amount=Decimal("500"), dose_unit="mg")
        assert _dose_in_name(parsed, "타이레놀정500밀리그램")

    def test_no_dose_always_matches(self):
        parsed = _parsed("암로디핀정")
        assert _dose_in_name(parsed, "암로디핀정5밀리그램")

    def test_dose_mismatch_returns_false(self):
        parsed = _parsed("타이레놀정", dose_amount=Decimal("160"), dose_unit="mg")
        assert not _dose_in_name(parsed, "타이레놀정500밀리그램")

    def test_dose_as_int_matches(self):
        # Decimal("500") == 500 → "500" should match "타이레놀정500밀리그램"
        parsed = _parsed("타이레놀정", dose_amount=Decimal("500"), dose_unit="mg")
        assert _dose_in_name(parsed, "타이레놀정500밀리그램")

    def test_decimal_dose_matches(self):
        parsed = _parsed("암로디핀정", dose_amount=Decimal("2.5"), dose_unit="mg")
        assert _dose_in_name(parsed, "암로디핀정2.5밀리그램")


# ── ExactIlikeAdapter ──────────────────────────────────────────────

class TestExactIlikeAdapter:
    @pytest.fixture
    def pool(self):
        p = MagicMock()
        p.acquire.return_value.__aenter__ = AsyncMock()
        p.acquire.return_value.__aexit__ = AsyncMock(return_value=False)
        return p

    async def test_returns_candidate_when_dose_matches(self, pool):
        conn = pool.acquire.return_value.__aenter__.return_value
        conn.fetch = AsyncMock(
            return_value=[_row("KD001", "타이레놀정500밀리그램")]
        )
        adapter = ExactIlikeAdapter(pool=pool)
        parsed = _parsed("타이레놀정", dose_amount=Decimal("500"), dose_unit="mg")
        result = await adapter.search_single(parsed)
        assert result is not None
        assert result.item_seq == "KD001"
        assert result.name == "타이레놀정500밀리그램"

    async def test_returns_none_when_no_dose_match(self, pool):
        conn = pool.acquire.return_value.__aenter__.return_value
        conn.fetch = AsyncMock(
            return_value=[_row("KD001", "타이레놀정160밀리그램")]
        )
        adapter = ExactIlikeAdapter(pool=pool)
        parsed = _parsed("타이레놀정", dose_amount=Decimal("500"), dose_unit="mg")
        result = await adapter.search_single(parsed)
        assert result is None

    async def test_returns_none_when_db_empty(self, pool):
        conn = pool.acquire.return_value.__aenter__.return_value
        conn.fetch = AsyncMock(return_value=[])
        adapter = ExactIlikeAdapter(pool=pool)
        parsed = _parsed("없는약정", dose_amount=Decimal("100"), dose_unit="mg")
        result = await adapter.search_single(parsed)
        assert result is None

    async def test_candidate_fields_populated(self, pool):
        conn = pool.acquire.return_value.__aenter__.return_value
        conn.fetch = AsyncMock(
            return_value=[_row("KD042", "암로디핀정5밀리그램", "ㅇㅁㄹㄷㅍㅈ5ㅁㄹㄱ")]
        )
        adapter = ExactIlikeAdapter(pool=pool)
        parsed = _parsed("암로디핀정", dose_amount=Decimal("5"), dose_unit="mg")
        result = await adapter.search_single(parsed)
        assert result is not None
        assert isinstance(result, Candidate)
        assert result.item_seq == "KD042"


# ── IlikeMultiAdapter ──────────────────────────────────────────────

class TestIlikeMultiAdapter:
    @pytest.fixture
    def pool(self):
        p = MagicMock()
        p.acquire.return_value.__aenter__ = AsyncMock()
        p.acquire.return_value.__aexit__ = AsyncMock(return_value=False)
        return p

    async def test_returns_candidates(self, pool):
        conn = pool.acquire.return_value.__aenter__.return_value
        conn.fetch = AsyncMock(
            return_value=[
                _row("KD001", "타이레놀정160밀리그램"),
                _row("KD002", "타이레놀정500밀리그램"),
            ]
        )
        adapter = IlikeMultiAdapter(pool=pool)
        parsed = _parsed("타이레놀정")
        results = await adapter.search(parsed)
        assert len(results) == 2
        assert all(isinstance(c, Candidate) for c in results)

    async def test_returns_empty_when_no_match(self, pool):
        conn = pool.acquire.return_value.__aenter__.return_value
        conn.fetch = AsyncMock(return_value=[])
        adapter = IlikeMultiAdapter(pool=pool)
        parsed = _parsed("없는약")
        results = await adapter.search(parsed)
        assert results == []

    async def test_candidate_item_seq_maps_kd_code(self, pool):
        conn = pool.acquire.return_value.__aenter__.return_value
        conn.fetch = AsyncMock(return_value=[_row("KD999", "테스트정")])
        adapter = IlikeMultiAdapter(pool=pool)
        results = await adapter.search(_parsed("테스트정"))
        assert results[0].item_seq == "KD999"


# ── TrigramMultiAdapter ────────────────────────────────────────────

class TestTrigramMultiAdapter:
    async def test_returns_candidates_from_trgm(self):
        from app.rag.ocr.fuzzy_search import FuzzyCandidate, JamoFuzzyRanker, TrigramFuzzySearch

        fuzzy_result = [
            FuzzyCandidate(
                kd_code="KD111",
                name="암로디핀정5밀리그램",
                name_jamo=jamotools.split_syllables("암로디핀정5밀리그램"),
                trgm_score=0.6,
            )
        ]

        class StubTrgm:
            async def search(self, query: str) -> list[FuzzyCandidate]:
                return fuzzy_result

        adapter = TrigramMultiAdapter(trgm_search=StubTrgm(), ranker=JamoFuzzyRanker())
        parsed = _parsed("암로디핀정")
        results = await adapter.search(parsed)
        assert len(results) >= 1
        assert results[0].item_seq == "KD111"

    async def test_returns_empty_when_no_match(self):
        class StubTrgm:
            async def search(self, query: str) -> list:
                return []

        from app.rag.ocr.fuzzy_search import JamoFuzzyRanker
        adapter = TrigramMultiAdapter(trgm_search=StubTrgm(), ranker=JamoFuzzyRanker())
        results = await adapter.search(_parsed("없는약"))
        assert results == []


# ── VectorMultiAdapter ─────────────────────────────────────────────

class TestVectorMultiAdapter:
    async def test_returns_candidates_from_vector(self):
        from app.rag.retriever import RetrievedDrug

        class StubRetriever:
            async def search(self, query: str, top_k: int) -> list[RetrievedDrug]:
                return [
                    RetrievedDrug(
                        kd_code="KD222",
                        name="세티리진정10밀리그램",
                        efficacy=None,
                        dosage=None,
                        main_ingr=None,
                    )
                ]

        adapter = VectorMultiAdapter(retriever=StubRetriever())  # type: ignore[arg-type]
        results = await adapter.search(_parsed("세티리진"))
        assert len(results) == 1
        assert results[0].item_seq == "KD222"

    async def test_returns_empty_when_retriever_empty(self):
        class StubRetriever:
            async def search(self, query: str, top_k: int) -> list:
                return []

        adapter = VectorMultiAdapter(retriever=StubRetriever())  # type: ignore[arg-type]
        results = await adapter.search(_parsed("없는약"))
        assert results == []
