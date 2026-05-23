from __future__ import annotations

from decimal import Decimal

import pytest

from app.domain.ocr import OcrItem, RawOcrItem
from app.rag.ocr.matcher import DrugMatcher, IlikeDrugSearch, MatchCandidate, VectorDrugSearch


class StubIlikeSearch(IlikeDrugSearch):
    def __init__(self, result: MatchCandidate | None):
        self._result = result
        self.calls: list[str] = []

    async def search(self, name: str) -> MatchCandidate | None:
        self.calls.append(name)
        return self._result


class StubVectorSearch(VectorDrugSearch):
    def __init__(self, result: MatchCandidate | None):
        self._result = result
        self.calls: list[str] = []

    async def search(self, name: str) -> MatchCandidate | None:
        self.calls.append(name)
        return self._result


def _raw(name: str = "타이레놀", confidence: str = "0.90") -> RawOcrItem:
    return RawOcrItem(
        name_raw=name,
        dose_amount=Decimal("1.00"),
        dose_unit="정",
        frequency=3,
        duration_days=7,
        confidence=Decimal(confidence),
    )


@pytest.mark.asyncio
async def test_matcher_returns_kd_code_when_name_ilike_hits():
    ilike = StubIlikeSearch(MatchCandidate(kd_code="KD001", name="타이레놀500mg", score=Decimal("1.0")))
    vector = StubVectorSearch(None)
    matcher = DrugMatcher(ilike=ilike, vector=vector)

    result: OcrItem = await matcher.match(_raw("타이레놀", "0.90"))

    assert result.kd_code == "KD001"
    assert result.matched_name == "타이레놀500mg"
    assert vector.calls == []
    assert result.confidence == Decimal("0.90")


@pytest.mark.asyncio
async def test_matcher_falls_back_to_vector_when_ilike_misses():
    ilike = StubIlikeSearch(None)
    vector = StubVectorSearch(MatchCandidate(kd_code="KD002", name="아스피린100mg", score=Decimal("0.80")))
    matcher = DrugMatcher(ilike=ilike, vector=vector)

    result = await matcher.match(_raw("아스피린", "0.95"))

    assert result.kd_code == "KD002"
    assert result.matched_name == "아스피린100mg"
    assert result.confidence == Decimal("0.80")


@pytest.mark.asyncio
async def test_matcher_returns_none_when_both_fail():
    ilike = StubIlikeSearch(None)
    vector = StubVectorSearch(None)
    matcher = DrugMatcher(ilike=ilike, vector=vector)

    result = await matcher.match(_raw("듣보잡약", "0.40"))

    assert result.kd_code is None
    assert result.matched_name is None
    assert result.confidence == Decimal("0.40")


@pytest.mark.asyncio
async def test_matcher_filters_vector_score_below_threshold():
    ilike = StubIlikeSearch(None)
    vector = StubVectorSearch(MatchCandidate(kd_code="KD002", name="아스피린", score=Decimal("0.55")))
    matcher = DrugMatcher(ilike=ilike, vector=vector)

    result = await matcher.match(_raw("아스피린", "0.95"))

    assert result.kd_code is None
    assert result.matched_name is None
