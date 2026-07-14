"""
#152 T-AI-DOSE-VERIFY-SHORTCIRCUIT — 용량 검증 게이트 단위 테스트

TDD RED → GREEN:
1. StrongExactAdapter._pick_best: 용량 불일치 시 None 반환
2. StrongExactAdapter._pick_best: 브랜드명 suffix mismatch 시 None 반환
3. MatchDecider.decide: dose_mismatch → CONFIRM
4. RrfMatcherAdapter.match: raw.dose_amount 보충
5. rrf_matcher: exact_fast final_score = 1.0

T-AI-DOSE-NULL-CONFIRM (Adversarial 발견, 2026-07-14) — dose_amount=None 다강도 약
오확정 AUTO 차단:
6. StrongExactAdapter._pick_best: dose=None + pool 내 강도 이질(5mg/10mg) → None
7. StrongExactAdapter._pick_best: dose=None + 단일 강도 약 → 기존 AUTO 유지
8. MatchDecider._dose_variants: 강도 제거 기준명 비교로 dose_amount 채워진
   후보들의 강도 다변 탐지 재활성화 (기존엔 c.dose_amount 가 항상 None 이라 dead code)
"""
from __future__ import annotations

from dataclasses import replace
from decimal import Decimal
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import jamotools
import pytest

from app.rag.ocr.decider import MatchDecider
from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.rrf import Candidate, MatchDecision, MatchDecisionType
from app.rag.ocr.rrf_adapters import (
    StrongExactAdapter,
    _has_form_suffix,
    _prefix_compatible,
)


# ── fixture helpers ────────────────────────────────────────────────────────────

def _parsed(
    name: str,
    dose_amount: Decimal | None = None,
    dose_unit: str | None = None,
) -> ParsedItem:
    return ParsedItem(
        raw=name,
        name=name,
        dose_amount=dose_amount,
        dose_unit=dose_unit,
        form=None,
        name_jamo=jamotools.split_syllables(name),
        is_valid=True,
        validation_errors=[],
    )


def _row(kd_code: str, name: str) -> dict:
    return {
        "kd_code": kd_code,
        "name": name,
        "name_jamo": jamotools.split_syllables(name),
    }


def _candidate(name: str, kd_code: str = "KD999", score: float = 0.9) -> Candidate:
    return Candidate(
        item_seq=kd_code,
        name=name,
        dose_amount=None,
        dose_unit=None,
        form=None,
        alias_source=None,
        name_jamo=jamotools.split_syllables(name),
        final_score=score,
    )


# ── _has_form_suffix ──────────────────────────────────────────────────────────

class TestHasFormSuffix:
    def test_정_suffix(self):
        assert _has_form_suffix("이세틸정")

    def test_캡슐_suffix(self):
        assert _has_form_suffix("아목시실린캡슐")

    def test_no_suffix_inn(self):
        assert not _has_form_suffix("메트포르민")

    def test_no_suffix_english(self):
        assert not _has_form_suffix("Tylenol")


# ── _prefix_compatible ────────────────────────────────────────────────────────

class TestPrefixCompatible:
    def test_query_is_prefix_of_name(self):
        assert _prefix_compatible("리피토정", "리피토정10밀리그램(아토르바스타틴칼슘)")

    def test_name_is_prefix_of_query(self):
        assert _prefix_compatible("타이레놀이알서방정", "타이레놀이알서방정200밀리그램")

    def test_manufacturer_prefix_2chars(self):
        # '레보' (2자) + '세티리진' → 레보세티리진
        assert _prefix_compatible("세티리진", "레보세티리진정5밀리그램")

    def test_manufacturer_prefix_3chars(self):
        # '종근당' (3자) + '아목시실린' → 종근당아목시실린캡슐
        assert _prefix_compatible("아목시실린캡슐", "종근당아목시실린캡슐500밀리그램")

    def test_manufacturer_prefix_4chars(self):
        # '영일염산' (4자) + '메트포르민' → 영일염산메트포르민
        assert _prefix_compatible("메트포르민", "영일염산메트포르민정500밀리그램")

    def test_suffix_match_rejected(self):
        # '이세틸정' 은 '케이세틸정' 의 suffix (offset=1 < 2) → 거부
        assert not _prefix_compatible("이세틸정", "케이세틸정(아세틸-L-카르니틴염산염)")

    def test_entirely_different_brand_rejected(self):
        # '이세틸정' (4자) 이 '케이세틸정' (5자) 의 offset=1 suffix → 거부
        # offset=1 < 2 (최소 허용 제조사 prefix 길이) → _prefix_compatible False
        assert not _prefix_compatible("이세틸정", "케이세틸정")

    def test_exact_match(self):
        assert _prefix_compatible("뮤테란캡슐", "뮤테란캡슐200밀리그램(아세틸시스테인)")


# ── StrongExactAdapter._pick_best ─────────────────────────────────────────────

class TestPickBest:
    def test_dose_mismatch_returns_none(self):
        """용량 명시했는데 후보명에 해당 용량 없음 → None (RRF 위임)."""
        parsed = _parsed("비유피-4정", dose_amount=Decimal("20"), dose_unit="mg")
        rows = [_row("KD001", "비유피-4정10밀리그램(우르소데옥시콜산)")]
        result = StrongExactAdapter._pick_best("비유피-4정", rows, parsed)
        assert result is None

    def test_dose_match_returns_candidate(self):
        """용량 일치하는 후보 → 반환."""
        parsed = _parsed("비유피-4정", dose_amount=Decimal("10"), dose_unit="mg")
        rows = [_row("KD001", "비유피-4정10밀리그램(우르소데옥시콜산)")]
        result = StrongExactAdapter._pick_best("비유피-4정", rows, parsed)
        assert result is not None
        assert "10밀리그램" in result.name

    def test_no_dose_returns_first(self):
        """dose_amount 없으면 dose 검증 없이 첫 후보 반환."""
        parsed = _parsed("타이레놀정")
        rows = [_row("KD002", "타이레놀정500밀리그램")]
        result = StrongExactAdapter._pick_best("타이레놀정", rows, parsed)
        assert result is not None

    def test_brand_name_suffix_mismatch_returns_none(self):
        """브랜드명 쿼리(정 접미사)가 후보명의 중간/끝에서만 매칭 → None."""
        parsed = _parsed("이세틸정")
        rows = [_row("KD003", "케이세틸정(아세틸-L-카르니틴염산염)")]
        # query '이세틸정' 은 '케이세틸정' 의 offset=1 suffix → prefix_compatible=False
        result = StrongExactAdapter._pick_best("이세틸정", rows, parsed)
        assert result is None

    def test_require_main_hit_none_when_no_prefix(self):
        """require_main_hit=True + prefix_compatible 없음 → None."""
        parsed = _parsed("타이레")
        rows = [_row("KD004", "독타이레정(성분명)")]  # 타이레 is not prefix
        result = StrongExactAdapter._pick_best("타이레", rows, parsed, require_main_hit=True)
        assert result is None

    def test_manufacturer_prefix_name_accepted(self):
        """제조사 prefix(3자) 제거 후 query 가 시작 → 허용."""
        parsed = _parsed("아목시실린캡슐")
        rows = [_row("KD005", "종근당아목시실린캡슐500밀리그램")]
        result = StrongExactAdapter._pick_best("아목시실린캡슐", rows, parsed)
        assert result is not None

    # ── T-AI-DOSE-NULL-CONFIRM: dose=None 다강도 오확정 방지 ──────────────────

    def test_dose_none_multi_strength_pool_returns_none(self):
        """Adversarial 실증: dose=None + pool 에 5mg/10mg 두 강도 → None (단축 금지).

        기존 버그: dose_amount=None 이면 dose 필터를 건너뛰고 pool[0](최단 이름,
        보통 저강도)을 그대로 반환 → 노바스크정10mg 이 정답인데 5mg 로 AUTO 오확정.
        """
        parsed = _parsed("노바스크정")  # vision 이 함량 못 읽어 dose_amount=None
        rows = [
            _row("KD010", "노바스크정5밀리그램(암로디핀베실산염)"),
            _row("KD011", "노바스크정10밀리그램(암로디핀베실산염)"),
        ]
        result = StrongExactAdapter._pick_best("노바스크정", rows, parsed)
        assert result is None

    def test_dose_none_single_strength_pool_still_auto(self):
        """dose=None 이어도 pool 이 단일 강도 약이면 기존 AUTO 유지 (과잉 CONFIRM 방지)."""
        parsed = _parsed("뮤테란캡슐")
        rows = [_row("KD012", "뮤테란캡슐200밀리그램(아세틸시스테인)")]
        result = StrongExactAdapter._pick_best("뮤테란캡슐", rows, parsed)
        assert result is not None
        assert result.item_seq == "KD012"

    def test_dose_none_no_dose_info_pool_still_auto(self):
        """dose=None + 후보명에도 강도 정보가 전혀 없는 복합제 → 기존 AUTO 유지."""
        parsed = _parsed("메가트루정")
        rows = [_row("KD013", "메가트루정(비타민복합제)")]
        result = StrongExactAdapter._pick_best("메가트루정", rows, parsed)
        assert result is not None

    def test_dose_none_mixed_known_unknown_returns_none(self):
        """dose=None + pool 에 강도 파싱 가능/불가 후보 혼재 → 검증 불가 → 보수적 None."""
        parsed = _parsed("혼합정")
        rows = [
            _row("KD014", "혼합정5밀리그램(성분A)"),
            _row("KD015", "혼합정(성분A)"),  # 강도 표기 없음
        ]
        result = StrongExactAdapter._pick_best("혼합정", rows, parsed)
        assert result is None


class TestDoseVariantsReactivated:
    """MatchDecider._dose_variants — Candidate.dose_amount 채움으로 dead code 재활성화."""

    def test_detects_variants_by_base_name_not_full_name(self):
        """전체 이름이 아닌 강도 제거 기준명으로 비교해야 다른 강도의 같은 약을 찾는다."""
        top1 = _candidate("노바스크정5밀리그램(암로디핀베실산염)", kd_code="KD010")
        top1.dose_amount = Decimal("5")
        other = _candidate("노바스크정10밀리그램(암로디핀베실산염)", kd_code="KD011")
        other.dose_amount = Decimal("10")

        variants = MatchDecider._dose_variants([top1, other], top1.name)
        assert len(variants) == 2

    def test_single_strength_no_variants(self):
        """단일 강도 약(같은 강도만 존재) → variants 1개(자기 자신) → CONFIRM 트리거 안 됨."""
        top1 = _candidate("뮤테란캡슐200밀리그램(아세틸시스테인)", kd_code="KD012")
        top1.dose_amount = Decimal("200")

        variants = MatchDecider._dose_variants([top1], top1.name)
        assert len(variants) == 1

    def test_decide_dose_unknown_confirm_with_populated_dose_amount(self):
        """MatchDecider.decide 전체 경로: dose=None + ranked 에 강도 다변 → CONFIRM(dose_unknown)."""
        parsed = _parsed("노바스크정")
        top1 = _candidate("노바스크정5밀리그램(암로디핀베실산염)", kd_code="KD010", score=0.91)
        top1.dose_amount = Decimal("5")
        other = _candidate("노바스크정10밀리그램(암로디핀베실산염)", kd_code="KD011", score=0.80)
        other.dose_amount = Decimal("10")

        decision = MatchDecider().decide(parsed, [top1, other])
        assert decision.type == MatchDecisionType.CONFIRM
        assert decision.reason == "dose_unknown"


# ── MatchDecider.decide — dose_mismatch ───────────────────────────────────────

class TestMatchDeciderDoseMismatch:
    def _decider(self) -> MatchDecider:
        return MatchDecider()

    def test_dose_mismatch_returns_confirm(self):
        """OCR 용량(150mg) ≠ 후보명 용량(50mg) → CONFIRM."""
        parsed = _parsed("엔테론정", dose_amount=Decimal("150"), dose_unit="mg")
        ranked = [_candidate("엔테론정50밀리그램(트리메부틴마레산염)", score=0.92)]
        decision = self._decider().decide(parsed, ranked)
        assert decision.type == MatchDecisionType.CONFIRM
        assert decision.reason == "dose_mismatch"

    def test_no_dose_in_candidate_returns_confirm(self):
        """후보명에 용량 없고 OCR 에 용량 있음 → CONFIRM (검증 불가)."""
        parsed = _parsed("이세틸정", dose_amount=Decimal("100"), dose_unit="mg")
        ranked = [_candidate("케이세틸정(아세틸-L-카르니틴염산염)", score=0.88)]
        decision = self._decider().decide(parsed, ranked)
        assert decision.type == MatchDecisionType.CONFIRM
        assert decision.reason == "dose_mismatch"

    def test_dose_match_returns_auto(self):
        """OCR 용량(10mg) = 후보명 용량(10밀리그램) → AUTO."""
        parsed = _parsed("리피토정", dose_amount=Decimal("10"), dose_unit="mg")
        ranked = [_candidate("리피토정10밀리그램(아토르바스타틴칼슘삼수화물)", score=0.95)]
        decision = self._decider().decide(parsed, ranked)
        assert decision.type == MatchDecisionType.AUTO

    def test_no_dose_in_parsed_skips_check(self):
        """parsed.dose_amount 없으면 dose_mismatch 체크 없이 통상 로직."""
        parsed = _parsed("암로디핀정")
        ranked = [_candidate("노바스크정5밀리그램(암로디핀베실산염)", score=0.91)]
        decision = self._decider().decide(parsed, ranked)
        # dose 체크 없음 → ambiguous or auto (단일 후보 → auto 가능)
        assert decision.type in (MatchDecisionType.AUTO, MatchDecisionType.CONFIRM)


# ── RrfMatcherAdapter dose enrich ─────────────────────────────────────────────

class TestRrfMatcherAdapterDoseEnrich:
    @pytest.mark.asyncio
    async def test_raw_dose_enriches_parsed(self):
        """raw.dose_amount 있고 parsed.dose_amount 없으면 → parsed 에 보충해서 rrf 호출."""
        from app.domain.ocr import RawOcrItem
        from app.rag.ocr.rrf_wire import RrfMatcherAdapter

        inner = MagicMock()
        inner.match = AsyncMock(return_value=MagicMock())
        adapter = RrfMatcherAdapter(inner)

        parsed = _parsed("엔테론정")
        assert parsed.dose_amount is None

        raw = RawOcrItem(
            name_raw="엔테론정150밀리그램",
            confidence=0.95,
            dose_amount=Decimal("150"),
            dose_unit="mg",
        )
        await adapter.match(parsed, raw)

        call_parsed = inner.match.call_args[0][0]
        assert call_parsed.dose_amount == Decimal("150")
        assert call_parsed.dose_unit == "mg"

    @pytest.mark.asyncio
    async def test_parsed_dose_not_overwritten(self):
        """parsed.dose_amount 이미 있으면 raw.dose_amount 로 덮지 않음."""
        from app.domain.ocr import RawOcrItem
        from app.rag.ocr.rrf_wire import RrfMatcherAdapter

        inner = MagicMock()
        inner.match = AsyncMock(return_value=MagicMock())
        adapter = RrfMatcherAdapter(inner)

        parsed = _parsed("리피토정", dose_amount=Decimal("10"))
        raw = RawOcrItem(name_raw="리피토정10mg", confidence=0.9, dose_amount=Decimal("20"))
        await adapter.match(parsed, raw)

        call_parsed = inner.match.call_args[0][0]
        assert call_parsed.dose_amount == Decimal("10")  # 원래 값 유지


# ── rrf_matcher exact_fast score ──────────────────────────────────────────────

class TestExactFastScore:
    @pytest.mark.asyncio
    async def test_exact_fast_final_score_is_1(self):
        """exact_fast 단축 경로: decision.primary.final_score = 1.0 (리포트 정직성)."""
        from app.rag.ocr.rrf_matcher import RrfMatcher

        candidate = _candidate("타이레놀정500밀리그램", score=0.0)

        exact_single = MagicMock()
        exact_single.search_single = AsyncMock(return_value=candidate)

        matcher = RrfMatcher(
            exact_single=exact_single,
            retrievers={},
        )
        parsed = _parsed("타이레놀정")
        result = await matcher.match(parsed)

        assert result.stage == "exact_fast"
        assert result.final_score == 1.0
        assert result.decision is not None
        assert result.decision.primary is not None
        assert result.decision.primary.final_score == 1.0
