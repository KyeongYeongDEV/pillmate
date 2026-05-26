from __future__ import annotations

from decimal import Decimal

import pytest

from app.rag.ocr.rrf import Candidate, MatchDecision, MatchDecisionType, fuse_rrf


def _cand(item_seq: str, name: str = "약") -> Candidate:
    return Candidate(
        item_seq=item_seq,
        name=name,
        dose_amount=None,
        dose_unit=None,
        form=None,
        alias_source=None,
        name_jamo=None,
    )


def test_rrf_score_calculation():
    """rank=0 단일 후보 → rrf_score = 1 / (k+0+1)"""
    results = {"exact": [_cand("SEQ001", "암로디핀정")]}
    fused = fuse_rrf(results)
    assert len(fused) == 1
    assert abs(fused[0].rrf_score - 1.0 / 61) < 1e-9


def test_rrf_common_top_amplified():
    """두 retriever에 동일 item_seq 상위 → 점수 합산"""
    c1 = _cand("SEQ001")
    c2 = _cand("SEQ001")
    fused = fuse_rrf({"exact": [c1], "trgm": [c2]})
    assert len(fused) == 1
    expected = 1.0 / 61 + 1.0 / 61
    assert abs(fused[0].rrf_score - expected) < 1e-9


def test_rrf_deduplicates_same_item():
    """같은 item_seq가 여러 retriever에 나와도 단일 Candidate로 병합"""
    results = {
        "exact": [_cand("SEQ001"), _cand("SEQ002")],
        "trgm": [_cand("SEQ001"), _cand("SEQ003")],
    }
    fused = fuse_rrf(results)
    seqs = [c.item_seq for c in fused]
    assert len(seqs) == len(set(seqs))
    assert len(fused) == 3


def test_rrf_empty_retriever_handled():
    """빈 결과도 에러 없이 빈 리스트 반환"""
    assert fuse_rrf({}) == []
    assert fuse_rrf({"exact": [], "trgm": []}) == []
