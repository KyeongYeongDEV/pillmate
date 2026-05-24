from __future__ import annotations

from app.rag.ocr.normalizer import first_token, normalize_drug_name


def test_normalize_strips_korean_brackets():
    assert normalize_drug_name("이세틸정 (이세틸정 100mg)") == "이세틸정"


def test_normalize_strips_english_brackets():
    assert normalize_drug_name("[abc] foo 50mg") == "foo 50밀리그람"


def test_normalize_converts_mg_to_milligrams():
    assert (
        normalize_drug_name("동광나자티딘캡슐150mg (나자티딘 150mg)")
        == "동광나자티딘캡슐150밀리그람"
    )


def test_first_token_extracts_korean_prefix():
    assert first_token("동광나자티딘캡슐150mg") == "동광나자티딘캡슐"
