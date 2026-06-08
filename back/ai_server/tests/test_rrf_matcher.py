from __future__ import annotations

from decimal import Decimal

import jamotools
import pytest

from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.reranker import BgeRerankerAdapter
from app.rag.ocr.rrf import Candidate, MatchDecisionType
from app.rag.ocr.rrf_matcher import RrfMatcher


def _parsed_with_dose(name: str = "암로디핀정") -> ParsedItem:
    return ParsedItem(
        raw=name,
        name=name,
        dose_amount=Decimal("5"),
        dose_unit="mg",
        form="정",
        name_jamo=jamotools.split_syllables(name),
        is_valid=True,
        validation_errors=[],
    )


def _parsed_no_dose(name: str = "암로디핀") -> ParsedItem:
    return ParsedItem(
        raw=name,
        name=name,
        dose_amount=None,
        dose_unit=None,
        form=None,
        name_jamo=jamotools.split_syllables(name),
        is_valid=True,
        validation_errors=[],
    )


def _cand(item_seq: str = "SEQ001") -> Candidate:
    return Candidate(
        item_seq=item_seq,
        name="암로디핀정",
        dose_amount=Decimal("5"),
        dose_unit="mg",
        form="정",
        alias_source="product",
        name_jamo=jamotools.split_syllables("암로디핀정"),
    )


class _StubExactSingle:
    def __init__(self, result: Candidate | None) -> None:
        self._result = result

    async def search_single(self, parsed: ParsedItem) -> Candidate | None:
        return self._result


class _StubMultiRetriever:
    def __init__(self, candidates: list[Candidate]) -> None:
        self._candidates = candidates

    async def search(self, parsed: ParsedItem) -> list[Candidate]:
        return list(self._candidates)


@pytest.mark.asyncio
async def test_fast_path_exact_single():
    """함량 있고 단일 exact 매치 → stage=exact_fast, AUTO"""
    matcher = RrfMatcher(
        exact_single=_StubExactSingle(_cand("SEQ001")),
        retrievers={
            "exact": _StubMultiRetriever([]),
            "trgm": _StubMultiRetriever([]),
            "jamo": _StubMultiRetriever([]),
            "vector": _StubMultiRetriever([]),
        },
    )
    result = await matcher.match(_parsed_with_dose())
    assert result.stage == "exact_fast"
    assert result.decision.type == MatchDecisionType.AUTO


@pytest.mark.asyncio
async def test_fast_path_no_dose_skipped():
    """함량 없으면 fast path 건너뜀 → stage=rrf"""
    matcher = RrfMatcher(
        exact_single=_StubExactSingle(_cand("SEQ001")),
        retrievers={
            "exact": _StubMultiRetriever([_cand("SEQ001")]),
            "trgm": _StubMultiRetriever([]),
            "jamo": _StubMultiRetriever([]),
            "vector": _StubMultiRetriever([]),
        },
    )
    result = await matcher.match(_parsed_no_dose())
    assert result.stage == "rrf"


@pytest.mark.asyncio
async def test_full_flow_combines_retrievers():
    """4 retriever 결과 → RRF 융합 → MatchResult 반환"""
    matcher = RrfMatcher(
        exact_single=_StubExactSingle(None),
        retrievers={
            "exact": _StubMultiRetriever([_cand("SEQ001")]),
            "trgm": _StubMultiRetriever([_cand("SEQ001")]),
            "jamo": _StubMultiRetriever([]),
            "vector": _StubMultiRetriever([]),
        },
    )
    result = await matcher.match(_parsed_with_dose())
    assert result.item is not None or result.decision is not None


@pytest.mark.asyncio
async def test_full_flow_returns_manual_when_no_candidates():
    """모든 retriever 빈 결과 → MANUAL"""
    matcher = RrfMatcher(
        exact_single=_StubExactSingle(None),
        retrievers={
            "exact": _StubMultiRetriever([]),
            "trgm": _StubMultiRetriever([]),
            "jamo": _StubMultiRetriever([]),
            "vector": _StubMultiRetriever([]),
        },
    )
    result = await matcher.match(_parsed_with_dose())
    assert result.decision.type == MatchDecisionType.MANUAL


@pytest.mark.asyncio
async def test_bge_reranker_invoked_when_configured():
    """bge_reranker 가 설정되면 RRF 후 BGE 재정렬이 호출된다."""
    reranked_flag = [False]
    reranked_query: list[str] = []

    class SpyBgeReranker:
        def rerank(self, query: str, candidates: list[Candidate]) -> list[Candidate]:
            reranked_flag[0] = True
            reranked_query.append(query)
            return candidates

    matcher = RrfMatcher(
        exact_single=_StubExactSingle(None),
        retrievers={
            "exact": _StubMultiRetriever([_cand("SEQ001")]),
            "trgm": _StubMultiRetriever([]),
            "jamo": _StubMultiRetriever([]),
            "vector": _StubMultiRetriever([]),
        },
        bge_reranker=SpyBgeReranker(),  # type: ignore[arg-type]
    )
    parsed = _parsed_no_dose("암로디핀정")
    await matcher.match(parsed)

    assert reranked_flag[0], "BGE reranker 가 호출돼야 한다"
    assert reranked_query[0] == parsed.raw, "BGE query 는 parsed.raw 여야 한다"


@pytest.mark.asyncio
async def test_bge_reranker_not_invoked_when_none():
    """bge_reranker=None 이면 BGE 재정렬을 건너뛴다."""
    call_count = [0]

    class SpyBgeReranker:
        def rerank(self, query: str, candidates: list[Candidate]) -> list[Candidate]:
            call_count[0] += 1
            return candidates

    matcher = RrfMatcher(
        exact_single=_StubExactSingle(None),
        retrievers={
            "exact": _StubMultiRetriever([_cand("SEQ001")]),
            "trgm": _StubMultiRetriever([]),
            "jamo": _StubMultiRetriever([]),
            "vector": _StubMultiRetriever([]),
        },
        bge_reranker=None,
    )
    await matcher.match(_parsed_no_dose())

    assert call_count[0] == 0, "bge_reranker=None 이면 호출 없어야 한다"
