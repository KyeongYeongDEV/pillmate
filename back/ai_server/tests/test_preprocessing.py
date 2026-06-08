"""
Tier 0 preprocessing 단위 테스트 — TDD RED

strip_manufacturer_prefix + normalize_for_cascade 테스트.
실제 구현은 run_eval_full.py 가 아닌 app/rag/ocr/normalizer.py 에 위치.
"""
from __future__ import annotations

import pytest


class TestStripManufacturerPrefix:
    def test_strips_known_manufacturer_jonggeundang(self):
        from app.rag.ocr.normalizer import strip_manufacturer_prefix
        assert strip_manufacturer_prefix("종근당아목시실린캡슐500밀") == "아목시실린캡슐500밀"

    def test_strips_ocr_typo_of_manufacturer(self):
        """OCR 오인식 '중근당' → '종근당' typo 도 제거."""
        from app.rag.ocr.normalizer import strip_manufacturer_prefix
        assert strip_manufacturer_prefix("중근당아목시실린캡슐500밀") == "아목시실린캡슐500밀"

    def test_strips_hanmi_prefix(self):
        from app.rag.ocr.normalizer import strip_manufacturer_prefix
        assert strip_manufacturer_prefix("한미아모피실린캡슐") == "아모피실린캡슐"

    def test_no_strip_when_not_manufacturer(self):
        from app.rag.ocr.normalizer import strip_manufacturer_prefix
        assert strip_manufacturer_prefix("타이레놀정500밀리그램") == "타이레놀정500밀리그램"

    def test_no_strip_short_name_below_threshold(self):
        """제조사명보다 짧은 이름은 그대로 반환."""
        from app.rag.ocr.normalizer import strip_manufacturer_prefix
        assert strip_manufacturer_prefix("종근당") == "종근당"

    def test_strips_longest_prefix_first(self):
        """'한국화이자' 는 '한국' 보다 먼저 매칭."""
        from app.rag.ocr.normalizer import strip_manufacturer_prefix
        result = strip_manufacturer_prefix("한국화이자아지트로마이신정")
        assert result == "아지트로마이신정"

    def test_strips_ilsung_prefix(self):
        from app.rag.ocr.normalizer import strip_manufacturer_prefix
        assert strip_manufacturer_prefix("일동세파클러캡슐") == "세파클러캡슐"

    def test_strips_daewoong_prefix(self):
        from app.rag.ocr.normalizer import strip_manufacturer_prefix
        assert strip_manufacturer_prefix("대웅아세트아미노펜정") == "아세트아미노펜정"


class TestNormalizeForCascade:
    def test_manufacturer_strip_then_normalize(self):
        """제조사 제거 후 normalize_drug_name 적용."""
        from app.rag.ocr.normalizer import normalize_for_cascade
        result = normalize_for_cascade("중근당아목시실린캡슐500밀")
        assert result == "아목시실린캡슐"

    def test_plain_name_still_normalized(self):
        from app.rag.ocr.normalizer import normalize_for_cascade
        result = normalize_for_cascade("타이레놀정500밀리그람")
        assert result == "타이레놀정"

    def test_empty_after_strip_falls_back(self):
        """strip 후 빈 문자열이면 원본 반환."""
        from app.rag.ocr.normalizer import normalize_for_cascade
        result = normalize_for_cascade("종근당")
        assert len(result) > 0
