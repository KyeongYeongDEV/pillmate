"""
TDD RED — RrfMatcher 운영 와이어링 단위 테스트
- DrugMatcherPort 시그니처 호환 (match(parsed, raw))
- service._is_definitive() — AUTO/CONFIRM 에서 cascade 중단
- BGE graceful degrade (AttributeError → DomainReranker only)
"""
from __future__ import annotations

from decimal import Decimal
from unittest.mock import MagicMock, patch

import jamotools
import pytest

from app.domain.ocr import RawOcrItem
from app.rag.ocr.matcher import MatchResult
from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.rrf import Candidate, MatchDecision, MatchDecisionType
from app.rag.ocr.rrf_wire import RrfMatcherAdapter
from app.rag.ocr.service import OcrPrescriptionService


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


def _raw(name: str = "암로디핀정") -> RawOcrItem:
    return RawOcrItem(
        name_raw=name,
        confidence=Decimal("0.9"),
        dose_amount=None,
        dose_unit=None,
        duration_days=None,
        candidates=[],
        appearance=None,
    )


def _auto_decision(name: str = "암로디핀정") -> MatchDecision:
    c = Candidate(
        item_seq="KD001",
        name=name,
        dose_amount=None,
        dose_unit=None,
        form=None,
        alias_source=None,
        name_jamo=jamotools.split_syllables(name),
        final_score=0.8,
    )
    return MatchDecision(type=MatchDecisionType.AUTO, primary=c, options=[c], reason="confident")


def _confirm_decision() -> MatchDecision:
    c = Candidate(
        item_seq="KD002",
        name="암로디핀정5밀리그램",
        dose_amount=None,
        dose_unit=None,
        form=None,
        alias_source=None,
        name_jamo=jamotools.split_syllables("암로디핀정5밀리그램"),
        final_score=0.72,
    )
    return MatchDecision(type=MatchDecisionType.CONFIRM, primary=c, options=[c], reason="ambiguous")


def _manual_decision() -> MatchDecision:
    return MatchDecision(type=MatchDecisionType.MANUAL, primary=None, options=[], reason="low_score")


# ── RrfMatcherAdapter ──────────────────────────────────────────────

class TestRrfMatcherAdapter:
    async def test_adapter_accepts_parsed_and_raw(self):
        """match(parsed, raw) 시그니처로 RrfMatcher 위임한다."""

        class StubRrfMatcher:
            async def match(self, parsed: ParsedItem) -> MatchResult:
                return MatchResult(
                    item=None,
                    stage="rrf",
                    final_score=0.8,
                    decision=_auto_decision(),
                )

        adapter = RrfMatcherAdapter(rrf_matcher=StubRrfMatcher())  # type: ignore[arg-type]
        result = await adapter.match(_parsed(), _raw())
        assert result.stage == "rrf"
        assert result.decision is not None

    async def test_adapter_propagates_rrf_result_unchanged(self):
        class StubRrfMatcher:
            async def match(self, parsed: ParsedItem) -> MatchResult:
                return MatchResult(
                    item=None,
                    stage="exact_fast",
                    final_score=1.0,
                    decision=_auto_decision(),
                )

        adapter = RrfMatcherAdapter(rrf_matcher=StubRrfMatcher())  # type: ignore[arg-type]
        result = await adapter.match(_parsed(), _raw())
        assert result.stage == "exact_fast"
        assert result.final_score == 1.0


# ── service._is_definitive ─────────────────────────────────────────

class TestServiceIsDefinitive:
    """OcrPrescriptionService._is_definitive 를 직접 테스트."""

    def test_item_not_none_is_definitive(self):
        from app.domain.ocr import OcrItem
        item = OcrItem(
            kd_code="KD001",
            name_raw="암로디핀정",
            matched_name="암로디핀정5밀리그램",
            dose_amount=None,
            dose_unit=None,
            duration_days=None,
            confidence=Decimal("0.9"),
        )
        result = MatchResult(item=item, stage="ilike")
        assert OcrPrescriptionService._is_definitive(result)

    def test_auto_decision_is_definitive(self):
        result = MatchResult(item=None, stage="rrf", decision=_auto_decision())
        assert OcrPrescriptionService._is_definitive(result)

    def test_confirm_decision_is_definitive(self):
        result = MatchResult(item=None, stage="rrf", decision=_confirm_decision())
        assert OcrPrescriptionService._is_definitive(result)

    def test_manual_decision_not_definitive(self):
        result = MatchResult(item=None, stage="rrf", decision=_manual_decision())
        assert not OcrPrescriptionService._is_definitive(result)

    def test_none_decision_not_definitive(self):
        result = MatchResult(item=None, stage="rrf", decision=None)
        assert not OcrPrescriptionService._is_definitive(result)


# ── BGE graceful degrade ───────────────────────────────────────────

class TestBgeGracefulDegrade:
    async def test_bge_rerank_catches_attribute_error(self):
        """compute_score() 에서 AttributeError → candidates 그대로 반환."""
        from app.rag.ocr.reranker import BgeRerankerAdapter

        adapter = BgeRerankerAdapter()

        class _BrokenModel:
            def compute_score(self, pairs, normalize=True):
                raise AttributeError("XLMRobertaTokenizer has no attribute prepare_for_model")

        adapter._model = _BrokenModel()

        c = Candidate(
            item_seq="KD001",
            name="암로디핀정5밀리그램",
            dose_amount=None,
            dose_unit=None,
            form=None,
            alias_source=None,
            name_jamo=jamotools.split_syllables("암로디핀정5밀리그램"),
            final_score=0.3,
        )

        result = adapter.rerank("암로디핀정", [c])
        assert result == [c], "AttributeError 발생 시 입력 그대로 반환해야 한다"
        assert adapter._degraded, "_degraded 플래그가 설정돼야 한다"

    async def test_bge_degraded_flag_prevents_repeated_load(self):
        """_degraded=True 면 _load() 를 다시 호출하지 않는다."""
        from app.rag.ocr.reranker import BgeRerankerAdapter

        adapter = BgeRerankerAdapter()
        adapter._degraded = True
        load_called = [False]

        original_load = adapter._load

        def spy_load():
            load_called[0] = True
            return original_load()

        adapter._load = spy_load

        c = Candidate(
            item_seq="KD001",
            name="테스트정",
            dose_amount=None,
            dose_unit=None,
            form=None,
            alias_source=None,
            name_jamo=jamotools.split_syllables("테스트정"),
        )
        adapter.rerank("테스트", [c])
        assert not load_called[0], "_degraded 상태에선 _load 호출 X"
