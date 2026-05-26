from __future__ import annotations

from decimal import Decimal

import pytest

from app.rag.ocr.parser import ParsedItem, VALID_UNITS, _validate, parse_drug_item


def test_parse_dose_korean_mg():
    result = parse_drug_item("타이레놀500밀리그램")
    assert result.dose_amount == Decimal("500")
    assert result.dose_unit == "mg"


def test_parse_dose_english_mg():
    result = parse_drug_item("Tylenol500mg")
    assert result.dose_amount == Decimal("500")
    assert result.dose_unit == "mg"


def test_parse_form_jeong():
    result = parse_drug_item("타이레놀정500mg")
    assert result.form == "정"


def test_parse_name_jamo():
    result = parse_drug_item("암로디핀")
    assert result.name_jamo == "ㅇㅏㅁㄹㅗㄷㅣㅍㅣㄴ"


def test_validate_short_name_fails():
    result = parse_drug_item("가")
    assert not result.is_valid
    assert "name_too_short" in result.validation_errors


def test_validate_unknown_unit_fails():
    is_valid, errors = _validate("타이레놀", "kg")
    assert not is_valid
    assert "unknown_unit:kg" in errors


def test_parse_complex():
    result = parse_drug_item("오페나딘서방정50밀리그람 (오페나딘염산염 50mg)")
    assert result.name == "오페나딘서방정"
    assert result.form == "정"
    assert result.dose_amount == Decimal("50")
    assert result.dose_unit == "mg"
