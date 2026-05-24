from __future__ import annotations

from app.rag.ocr.normalizer import (
    first_english_token,
    first_token,
    normalize_drug_name,
)


def test_normalize_strips_korean_brackets():
    assert normalize_drug_name("이세틸정 (이세틸정 100mg)") == "이세틸정"


def test_normalize_strips_english_brackets():
    assert normalize_drug_name("[abc] foo 50mg") == "foo"


def test_normalize_drops_unit_with_quantity():
    assert (
        normalize_drug_name("동광나자티딘캡슐150mg (나자티딘 150mg)")
        == "동광나자티딘캡슐"
    )


def test_normalize_drops_milligram_korean_both_spellings():
    assert normalize_drug_name("오페나딘서방정50밀리그람") == "오페나딘서방정"
    assert normalize_drug_name("오페나딘서방정50밀리그램") == "오페나딘서방정"


def test_normalize_drops_mixed_korean_english_unit():
    assert (
        normalize_drug_name("동광나자티딘캡슐150mg Nizatidine 150mg")
        == "동광나자티딘캡슐 Nizatidine"
    )


def test_first_token_extracts_korean_prefix():
    assert first_token("동광나자티딘캡슐150mg") == "동광나자티딘캡슐"


def test_first_token_extracts_korean_without_digits():
    assert first_token("동광나자티딘캡슐150밀리그람") == "동광나자티딘캡슐"


def test_first_english_token_extracts_english_word():
    assert (
        first_english_token("동광나자티딘캡슐150mg Nizatidine 150mg") == "Nizatidine"
    )


def test_first_english_token_returns_none_when_no_english():
    assert first_english_token("엔테론정150밀리그람") is None
