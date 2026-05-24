from __future__ import annotations

from decimal import Decimal

import pytest

from app.domain.ocr import RawOcrItem
from app.rag.ocr.matcher import (
    DrugMatcher,
    IlikeDrugSearch,
    IngredientSearch,
    MatchCandidate,
    MatchResult,
    VectorDrugSearch,
)


class StubIlikeSearch(IlikeDrugSearch):
    def __init__(self, results: list[MatchCandidate | None]):
        self._results = list(results)
        self.calls: list[str] = []

    async def search(self, name: str) -> MatchCandidate | None:
        self.calls.append(name)
        if not self._results:
            return None
        return self._results.pop(0)


class StubIngredientSearch(IngredientSearch):
    def __init__(self, result: MatchCandidate | None = None):
        self._result = result
        self.calls: list[str] = []

    async def search(self, ingredient: str) -> MatchCandidate | None:
        self.calls.append(ingredient)
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


def _matcher(
    ilike: StubIlikeSearch,
    vector: StubVectorSearch,
    ingredient: StubIngredientSearch | None = None,
) -> DrugMatcher:
    return DrugMatcher(
        ilike=ilike,
        vector=vector,
        ingredient=ingredient or StubIngredientSearch(None),
    )


@pytest.mark.asyncio
async def test_matcher_returns_kd_code_when_name_ilike_hits():
    ilike = StubIlikeSearch([MatchCandidate(kd_code="KD001", name="타이레놀500mg", score=Decimal("1.0"))])
    vector = StubVectorSearch(None)
    matcher = _matcher(ilike, vector)

    result: MatchResult = await matcher.match(_raw("타이레놀", "0.90"))

    assert result.item.kd_code == "KD001"
    assert result.item.matched_name == "타이레놀500mg"
    assert result.stage == "ilike"
    assert vector.calls == []
    assert result.item.confidence == Decimal("0.90")


@pytest.mark.asyncio
async def test_matcher_falls_back_to_vector_when_ilike_misses():
    ilike = StubIlikeSearch([None])
    vector = StubVectorSearch(MatchCandidate(kd_code="KD002", name="아스피린100mg", score=Decimal("0.80")))
    matcher = _matcher(ilike, vector)

    result = await matcher.match(_raw("아스피린", "0.95"))

    assert result.item.kd_code == "KD002"
    assert result.item.matched_name == "아스피린100mg"
    assert result.stage == "vector"
    assert result.item.confidence == Decimal("0.80")


@pytest.mark.asyncio
async def test_matcher_returns_none_when_both_fail():
    ilike = StubIlikeSearch([None])
    vector = StubVectorSearch(None)
    matcher = _matcher(ilike, vector)

    result = await matcher.match(_raw("듣보잡약", "0.40"))

    assert result.item.kd_code is None
    assert result.item.matched_name is None
    assert result.stage == "none"
    assert result.item.confidence == Decimal("0.40")


@pytest.mark.asyncio
async def test_matcher_filters_vector_score_below_threshold():
    ilike = StubIlikeSearch([None])
    vector = StubVectorSearch(MatchCandidate(kd_code="KD002", name="아스피린", score=Decimal("0.55")))
    matcher = _matcher(ilike, vector)

    result = await matcher.match(_raw("아스피린", "0.95"))

    assert result.item.kd_code is None
    assert result.item.matched_name is None
    assert result.stage == "none"


@pytest.mark.asyncio
async def test_matcher_strips_brackets_before_ilike():
    ilike = StubIlikeSearch([MatchCandidate(kd_code="KD010", name="이세틸정100밀리그램", score=Decimal("1.0"))])
    vector = StubVectorSearch(None)
    matcher = _matcher(ilike, vector)

    result = await matcher.match(_raw("이세틸정 (이세틸정 100mg)", "0.95"))

    assert ilike.calls == ["이세틸정"]
    assert result.item.kd_code == "KD010"
    assert result.stage == "ilike"


@pytest.mark.asyncio
async def test_matcher_normalizes_unit_away_before_ilike():
    ilike = StubIlikeSearch(
        [MatchCandidate(kd_code="KD020", name="동광나자티딘캡슐150밀리그램", score=Decimal("1.0"))]
    )
    vector = StubVectorSearch(None)
    matcher = _matcher(ilike, vector)

    result = await matcher.match(_raw("동광나자티딘캡슐150mg", "0.95"))

    assert ilike.calls == ["동광나자티딘캡슐"]
    assert result.item.kd_code == "KD020"
    assert result.stage == "ilike"


@pytest.mark.asyncio
async def test_matcher_falls_back_to_first_token_when_normalized_misses():
    ilike = StubIlikeSearch(
        [None, MatchCandidate(kd_code="KD030", name="오페나딘서방정50밀리그램", score=Decimal("0.95"))]
    )
    vector = StubVectorSearch(None)
    matcher = _matcher(ilike, vector)

    result = await matcher.match(_raw("오페나딘서방정50밀리그람 Ophenadine SR", "0.95"))

    assert ilike.calls == ["오페나딘서방정 Ophenadine SR", "오페나딘서방정"]
    assert result.item.kd_code == "KD030"
    assert result.stage == "token"
    assert vector.calls == []


@pytest.mark.asyncio
async def test_matcher_falls_back_to_ingredient_when_token_misses():
    ilike = StubIlikeSearch([None, None])
    ingredient = StubIngredientSearch(
        MatchCandidate(kd_code="KD040", name="나자티딘캡슐150mg", score=Decimal("0.85"))
    )
    vector = StubVectorSearch(None)
    matcher = _matcher(ilike, vector, ingredient=ingredient)

    result = await matcher.match(_raw("동광나자티딘캡슐150mg Nizatidine 150mg", "0.95"))

    assert ilike.calls == ["동광나자티딘캡슐 Nizatidine", "동광나자티딘캡슐"]
    assert ingredient.calls == ["Nizatidine"]
    assert result.item.kd_code == "KD040"
    assert result.stage == "ingredient"
    assert vector.calls == []


@pytest.mark.asyncio
async def test_matcher_skips_ingredient_when_no_english_token():
    ilike = StubIlikeSearch([None, None])
    ingredient = StubIngredientSearch(None)
    vector = StubVectorSearch(None)
    matcher = _matcher(ilike, vector, ingredient=ingredient)

    result = await matcher.match(_raw("엔테론정150밀리그람 포도씨건조엑스", "0.90"))

    assert ingredient.calls == []
    assert vector.calls == ["엔테론정150밀리그람 포도씨건조엑스"]
    assert result.stage == "none"


@pytest.mark.asyncio
async def test_matcher_returns_none_when_all_four_stages_fail():
    ilike = StubIlikeSearch([None, None])
    ingredient = StubIngredientSearch(None)
    vector = StubVectorSearch(None)
    matcher = _matcher(ilike, vector, ingredient=ingredient)

    result = await matcher.match(_raw("듣보잡캡슐150mg Unknownine", "0.40"))

    assert result.item.kd_code is None
    assert result.stage == "none"
    assert ilike.calls == ["듣보잡캡슐 Unknownine", "듣보잡캡슐"]
    assert ingredient.calls == ["Unknownine"]
    assert vector.calls == ["듣보잡캡슐150mg Unknownine"]
