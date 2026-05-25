from __future__ import annotations

from decimal import Decimal

import jamotools
import Levenshtein
import pytest

from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.reranker import (
    ALIAS_INGREDIENT_BONUS,
    ALIAS_PRODUCT_BONUS,
    DOSE_MATCH_BONUS,
    DOSE_MISMATCH_PENALTY,
    FORM_MATCH_BONUS,
    JAMO_PENALTY_PER_CHAR,
    DomainReranker,
)
from app.rag.ocr.rrf import Candidate


def _parsed(
    name: str = "암로디핀정",
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


def _cand(
    item_seq: str = "SEQ001",
    dose_amount: Decimal | None = None,
    dose_unit: str | None = None,
    form: str | None = None,
    alias_source: str | None = None,
    name_jamo: str | None = None,
    rrf_score: float = 0.1,
) -> Candidate:
    return Candidate(
        item_seq=item_seq,
        name="약",
        dose_amount=dose_amount,
        dose_unit=dose_unit,
        form=form,
        alias_source=alias_source,
        name_jamo=name_jamo,
        rrf_score=rrf_score,
    )


def test_dose_match_bonus():
    reranker = DomainReranker()
    parsed = _parsed(dose_amount=Decimal("5"), dose_unit="mg")
    cand = _cand(dose_amount=Decimal("5"), dose_unit="mg")
    result = reranker.rerank(parsed, [cand])
    assert abs(result[0].final_score - (0.1 + DOSE_MATCH_BONUS)) < 1e-9


def test_dose_mismatch_penalty():
    reranker = DomainReranker()
    parsed = _parsed(dose_amount=Decimal("5"), dose_unit="mg")
    cand = _cand(dose_amount=Decimal("10"), dose_unit="mg")
    result = reranker.rerank(parsed, [cand])
    assert abs(result[0].final_score - (0.1 + DOSE_MISMATCH_PENALTY)) < 1e-9


def test_form_match_bonus():
    reranker = DomainReranker()
    parsed = _parsed(form="정")
    cand = _cand(form="정")
    result = reranker.rerank(parsed, [cand])
    assert abs(result[0].final_score - (0.1 + FORM_MATCH_BONUS)) < 1e-9


def test_alias_product_bonus():
    reranker = DomainReranker()
    parsed = _parsed()
    cand = _cand(alias_source="product")
    result = reranker.rerank(parsed, [cand])
    assert abs(result[0].final_score - (0.1 + ALIAS_PRODUCT_BONUS)) < 1e-9


def test_alias_ingredient_bonus():
    reranker = DomainReranker()
    parsed = _parsed()
    cand = _cand(alias_source="ingredient")
    result = reranker.rerank(parsed, [cand])
    assert abs(result[0].final_score - (0.1 + ALIAS_INGREDIENT_BONUS)) < 1e-9


def test_jamo_distance_penalty():
    reranker = DomainReranker()
    query_jamo = jamotools.split_syllables("암로디핀")
    cand_jamo = jamotools.split_syllables("암르디핀")
    dist = Levenshtein.distance(query_jamo, cand_jamo)
    parsed = _parsed(name="암로디핀")
    cand = _cand(name_jamo=cand_jamo)
    result = reranker.rerank(parsed, [cand])
    expected = 0.1 - dist * JAMO_PENALTY_PER_CHAR
    assert abs(result[0].final_score - expected) < 1e-9


def test_rerank_orders_by_final_score():
    reranker = DomainReranker()
    parsed = _parsed(dose_amount=Decimal("5"), dose_unit="mg")
    good = _cand(item_seq="SEQ001", dose_amount=Decimal("5"), dose_unit="mg", rrf_score=0.1)
    bad = _cand(item_seq="SEQ002", dose_amount=Decimal("10"), dose_unit="mg", rrf_score=0.1)
    result = reranker.rerank(parsed, [bad, good])
    assert result[0].item_seq == "SEQ001"
