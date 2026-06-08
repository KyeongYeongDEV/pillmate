"""
Tier 1 fuzzy 자모 prefix_match 단위 테스트 — TDD RED

JamoFuzzyRanker.rerank(prefix_match=True) 로
쎌박타민정500밀리 → 썰박타민정500밀리그램 같은 모음 OCR 오류 매칭 검증.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal

import jamotools
import pytest


def _make_candidate(name: str) -> object:
    from app.rag.ocr.fuzzy_search import FuzzyCandidate
    return FuzzyCandidate(
        kd_code="KD_TEST",
        name=name,
        name_jamo=jamotools.split_syllables(name),
        trgm_score=0.5,
    )


class TestJamoPrefixMatch:
    def test_prefix_match_catches_vowel_ocr_error(self):
        """'쎌박타민정' query → '썰박타민정500밀리그램' DB hit (prefix_match=True)."""
        from app.rag.ocr.fuzzy_search import JamoFuzzyRanker

        query_name = "쎌박타민정"  # OCR error: ㅔ instead of ㅓ
        db_name = "썰박타민정500밀리그램"  # Correct DB drug name

        query_jamo = jamotools.split_syllables(query_name)
        candidates = [_make_candidate(db_name)]
        ranker = JamoFuzzyRanker()

        # Without prefix_match=True should fail (distance > threshold due to extra "500밀리그램")
        result_no_prefix = ranker.rerank(query_jamo, candidates, prefix_match=False)

        # With prefix_match=True should match (only compare first len(query_jamo) chars)
        result_prefix = ranker.rerank(query_jamo, candidates, prefix_match=True)

        assert len(result_no_prefix) == 0, "Distance too large without prefix_match"
        assert len(result_prefix) == 1, "Should match with prefix_match=True"

    def test_prefix_match_exact_name_still_works(self):
        """정확한 이름도 prefix_match=True 에서 정상 매칭."""
        from app.rag.ocr.fuzzy_search import JamoFuzzyRanker

        query_name = "타이레놀정"
        db_name = "타이레놀정500밀리그람(아세트아미노펜)"
        query_jamo = jamotools.split_syllables(query_name)
        candidates = [_make_candidate(db_name)]
        ranker = JamoFuzzyRanker()

        result = ranker.rerank(query_jamo, candidates, prefix_match=True)
        assert len(result) == 1

    def test_prefix_match_false_is_default(self):
        """prefix_match 기본값은 False — 기존 동작 유지."""
        from app.rag.ocr.fuzzy_search import JamoFuzzyRanker
        import inspect
        sig = inspect.signature(JamoFuzzyRanker.rerank)
        assert "prefix_match" in sig.parameters
        assert sig.parameters["prefix_match"].default is False

    def test_prefix_match_rejects_completely_different_name(self):
        """완전히 다른 약품명은 prefix_match=True 에서도 제외."""
        from app.rag.ocr.fuzzy_search import JamoFuzzyRanker

        query_name = "타이레놀정"
        db_name = "암로디핀베실산염정500밀리그램"
        query_jamo = jamotools.split_syllables(query_name)
        candidates = [_make_candidate(db_name)]
        ranker = JamoFuzzyRanker()

        result = ranker.rerank(query_jamo, candidates, prefix_match=True)
        assert len(result) == 0
