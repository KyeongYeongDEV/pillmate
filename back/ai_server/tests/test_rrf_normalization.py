"""
TDD RED — DomainReranker-only sigmoid 정규화 테스트
BGE 없을 때 final_score가 [0,1] 범위로 정규화되는지 검증.
"""
from __future__ import annotations

import math
from decimal import Decimal

import jamotools
import pytest

from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.rrf import Candidate, MatchDecisionType
from app.rag.ocr.rrf_matcher import RrfMatcher, _sigmoid


def _parsed(name: str = "암로디핀정") -> ParsedItem:
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


def _cand(item_seq: str = "SEQ001", dose: Decimal | None = None) -> Candidate:
    return Candidate(
        item_seq=item_seq,
        name="암로디핀정5밀리그램",
        dose_amount=dose,
        dose_unit="mg" if dose else None,
        form="정",
        alias_source=None,
        name_jamo=jamotools.split_syllables("암로디핀정5밀리그램"),
    )


class TestSigmoidUtil:
    def test_sigmoid_zero_is_half(self):
        assert abs(_sigmoid(0.0) - 0.5) < 1e-9

    def test_sigmoid_large_positive_near_one(self):
        assert _sigmoid(10.0) > 0.99

    def test_sigmoid_large_negative_near_zero(self):
        assert _sigmoid(-10.0) < 0.01

    def test_sigmoid_output_in_unit_interval(self):
        for x in [-5.0, -1.0, 0.0, 0.5, 1.0, 5.0]:
            s = _sigmoid(x)
            assert 0.0 < s < 1.0


class TestRrfMatcherNormalization:
    """BGE 없을 때 final_score가 0~1 범위로 정규화돼야 한다."""

    class _StubExact:
        async def search_single(self, parsed: ParsedItem) -> Candidate | None:
            return None

    class _StubMulti:
        def __init__(self, candidates: list[Candidate]):
            self._candidates = candidates

        async def search(self, parsed: ParsedItem) -> list[Candidate]:
            return list(self._candidates)

    async def test_final_score_in_unit_interval_without_bge(self):
        """BGE=None 일 때 final_score ∈ (0, 1)"""
        matcher = RrfMatcher(
            exact_single=self._StubExact(),
            retrievers={"ilike": self._StubMulti([_cand("SEQ001")])},
            bge_reranker=None,
        )
        result = await matcher.match(_parsed())
        if result.decision and result.decision.primary:
            score = result.decision.primary.final_score
            assert 0.0 < score < 1.0, f"final_score out of range: {score}"

    async def test_final_score_not_normalized_when_bge_present(self):
        """BGE 가 있으면 raw final_score 를 유지 (정규화 안 함)."""
        called = [False]

        class SpyBge:
            def rerank(self, query: str, candidates: list[Candidate]) -> list[Candidate]:
                called[0] = True
                # BGE 스코어를 0.85로 하드코딩 (정규화 대상이면 0~1 벗어나도 그대로)
                for c in candidates:
                    c.final_score = 0.85
                return candidates

        matcher = RrfMatcher(
            exact_single=self._StubExact(),
            retrievers={"ilike": self._StubMulti([_cand("SEQ001")])},
            bge_reranker=SpyBge(),  # type: ignore[arg-type]
        )
        result = await matcher.match(_parsed())
        assert called[0], "BGE 가 호출돼야 한다"
        if result.decision and result.decision.primary:
            # BGE 가 설정한 0.85 그대로 유지 (sigmoid 추가 적용 X)
            assert abs(result.decision.primary.final_score - 0.85) < 1e-6

    async def test_manual_result_has_no_primary_but_score_still_valid(self):
        """후보 없을 때 MANUAL, primary=None — score 범위 검증 불필요."""
        matcher = RrfMatcher(
            exact_single=self._StubExact(),
            retrievers={"ilike": self._StubMulti([])},
            bge_reranker=None,
        )
        result = await matcher.match(_parsed())
        assert result.decision is not None
        assert result.decision.type == MatchDecisionType.MANUAL
        assert result.decision.primary is None
