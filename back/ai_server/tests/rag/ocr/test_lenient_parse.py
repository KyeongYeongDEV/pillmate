"""
T-AI-OCR-LATENCY-30S — lenient_parse 부분 복구 TDD

RawOcrItemList 전체 파싱 실패 시, 재호출 전에 아이템별로 복구를 시도한다.
- name_raw 가 없거나 "???" 인 아이템은 드랍(unresolved).
- dose_amount/duration_days/frequency 가 숫자로 해석 안 되면 None(또는 기본값)으로 강등.
- confidence 가 숫자로 해석 안 되면 0.7 미만 안전값으로 강등(auto 임계치 미만 → MANUAL 유도).
- 이름은 절대 추측 보정하지 않는다(의료 안전).
"""
from __future__ import annotations

from decimal import Decimal

from app.domain.ocr import RawOcrItem


def test_all_items_valid_returns_all_survivors():
    from app.rag.ocr.lenient_parse import parse_items_leniently

    content = (
        '{"items": [{"name_raw": "타이레놀정", "confidence": 0.95, "dose_amount": 1}]}'
    )
    result = parse_items_leniently(content, RawOcrItem)

    assert len(result.items) == 1
    assert result.dropped_count == 0
    assert result.items[0].name_raw == "타이레놀정"


def test_unknown_name_sentinel_item_is_dropped_others_survive():
    from app.rag.ocr.lenient_parse import parse_items_leniently

    content = (
        '{"items": ['
        '{"name_raw": "???", "confidence": 0.9},'
        '{"name_raw": "게보린정", "confidence": 0.88}'
        ']}'
    )
    result = parse_items_leniently(content, RawOcrItem)

    assert result.dropped_count == 1
    assert len(result.items) == 1
    assert result.items[0].name_raw == "게보린정"


def test_empty_name_raw_item_is_dropped():
    from app.rag.ocr.lenient_parse import parse_items_leniently

    content = '{"items": [{"name_raw": "", "confidence": 0.9}, {"name_raw": "타이레놀", "confidence": 0.9}]}'
    result = parse_items_leniently(content, RawOcrItem)

    assert result.dropped_count == 1
    assert len(result.items) == 1


def test_corrupted_dose_amount_int_field_coerced_to_none_item_survives():
    from app.rag.ocr.lenient_parse import parse_items_leniently

    content = (
        '{"items": [{"name_raw": "타이레놀정", "confidence": 0.9, "dose_amount": "모름"}]}'
    )
    result = parse_items_leniently(content, RawOcrItem)

    assert result.dropped_count == 0
    assert len(result.items) == 1
    assert result.items[0].dose_amount is None
    assert result.items[0].name_raw == "타이레놀정"  # 이름은 그대로 — 추측 보정 없음


def test_corrupted_confidence_field_downgraded_below_auto_threshold():
    from app.rag.ocr.lenient_parse import parse_items_leniently

    content = '{"items": [{"name_raw": "타이레놀정", "confidence": "???"}]}'
    result = parse_items_leniently(content, RawOcrItem)

    assert len(result.items) == 1
    assert result.items[0].confidence < Decimal("0.7")  # MANUAL 유도 (medical-safety 불변)


def test_corrupted_frequency_falls_back_to_default():
    from app.domain.ocr import DEFAULT_FREQUENCY
    from app.rag.ocr.lenient_parse import parse_items_leniently

    content = (
        '{"items": [{"name_raw": "타이레놀정", "confidence": 0.9, "frequency": "세번"}]}'
    )
    result = parse_items_leniently(content, RawOcrItem)

    assert result.items[0].frequency == DEFAULT_FREQUENCY


def test_all_items_broken_returns_empty_with_dropped_count():
    from app.rag.ocr.lenient_parse import parse_items_leniently

    content = '{"items": [{"name_raw": "???", "confidence": 0.9}]}'
    result = parse_items_leniently(content, RawOcrItem)

    assert result.items == []
    assert result.dropped_count == 1


def test_has_resident_number_flag_preserved():
    from app.rag.ocr.lenient_parse import parse_items_leniently

    content = (
        '{"items": [{"name_raw": "타이레놀정", "confidence": 0.9}], '
        '"has_resident_number": true}'
    )
    result = parse_items_leniently(content, RawOcrItem)

    assert result.has_resident_number is True


def test_extracts_json_from_markdown_fence():
    from app.rag.ocr.lenient_parse import parse_items_leniently

    content = (
        "```json\n"
        '{"items": [{"name_raw": "타이레놀정", "confidence": 0.9}]}\n'
        "```"
    )
    result = parse_items_leniently(content, RawOcrItem)

    assert len(result.items) == 1


def test_extracts_json_with_surrounding_prose():
    from app.rag.ocr.lenient_parse import parse_items_leniently

    content = (
        '알겠습니다. 결과는 다음과 같습니다:\n'
        '{"items": [{"name_raw": "타이레놀정", "confidence": 0.9}]}\n'
        '이상입니다.'
    )
    result = parse_items_leniently(content, RawOcrItem)

    assert len(result.items) == 1
