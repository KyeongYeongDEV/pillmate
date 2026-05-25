from __future__ import annotations

from decimal import Decimal

import jamotools
import pytest

from app.rag.ocr.decider import ABS_THRESHOLD, MARGIN_THRESHOLD, MatchDecider
from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.rrf import Candidate, MatchDecisionType


def _parsed(name: str = "암로디핀정", dose_amount: Decimal | None = None) -> ParsedItem:
    return ParsedItem(
        raw=name,
        name=name,
        dose_amount=dose_amount,
        dose_unit="mg" if dose_amount else None,
        form="정",
        name_jamo=jamotools.split_syllables(name),
        is_valid=True,
        validation_errors=[],
    )


def _cand(
    item_seq: str,
    name: str = "암로디핀정",
    final_score: float = 0.80,
    dose_amount: Decimal | None = Decimal("5"),
) -> Candidate:
    c = Candidate(
        item_seq=item_seq,
        name=name,
        dose_amount=dose_amount,
        dose_unit="mg",
        form="정",
        alias_source=None,
        name_jamo=jamotools.split_syllables(name),
    )
    c.final_score = final_score
    return c


def test_decide_auto_when_high_score_and_margin():
    """top1 점수 ≥ ABS_THRESHOLD, 격차 ≥ MARGIN_THRESHOLD → AUTO"""
    decider = MatchDecider()
    top1 = _cand("SEQ001", final_score=0.85)
    top2 = _cand("SEQ002", final_score=0.70)
    decision = decider.decide(_parsed(), [top1, top2])
    assert decision.type == MatchDecisionType.AUTO
    assert decision.primary == top1
    assert decision.reason == "confident"


def test_decide_confirm_when_margin_small():
    """top1=0.85, top2=0.82 → 격차 0.03 < MARGIN_THRESHOLD → CONFIRM(ambiguous)"""
    decider = MatchDecider()
    top1 = _cand("SEQ001", final_score=0.85)
    top2 = _cand("SEQ002", final_score=0.82)
    decision = decider.decide(_parsed(), [top1, top2])
    assert decision.type == MatchDecisionType.CONFIRM
    assert decision.reason == "ambiguous"
    assert top1 in decision.options


def test_decide_confirm_when_dose_unknown_and_multi_variants():
    """함량 없고 같은 약명 다중 dose 후보 → CONFIRM(dose_unknown)"""
    decider = MatchDecider()
    v5 = _cand("SEQ001", name="암로디핀정", final_score=0.80, dose_amount=Decimal("5"))
    v10 = _cand("SEQ002", name="암로디핀정", final_score=0.70, dose_amount=Decimal("10"))
    # parsed has no dose_amount, top1-top2 margin is large enough
    decision = decider.decide(_parsed(dose_amount=None), [v5, v10])
    assert decision.type == MatchDecisionType.CONFIRM
    assert decision.reason == "dose_unknown"
    assert len(decision.options) >= 2


def test_decide_manual_when_low_score():
    """top1.final_score < ABS_THRESHOLD → MANUAL(low_score)"""
    decider = MatchDecider()
    low = _cand("SEQ001", final_score=0.60)
    decision = decider.decide(_parsed(), [low])
    assert decision.type == MatchDecisionType.MANUAL
    assert decision.reason == "low_score"


def test_decide_manual_when_no_candidates():
    """후보 없으면 → MANUAL(no_match)"""
    decider = MatchDecider()
    decision = decider.decide(_parsed(), [])
    assert decision.type == MatchDecisionType.MANUAL
    assert decision.reason == "no_match"
