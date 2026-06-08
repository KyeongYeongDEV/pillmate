"""
Tier 2 Vision candidates 단위 테스트 — TDD RED

RawOcrItem.candidates 필드 + GeminiVisionAdapter 의
MIME type 분기 + prompt 강화 검증.
"""
from __future__ import annotations

from decimal import Decimal

import pytest


class TestRawOcrItemCandidates:
    def test_candidates_field_default_empty(self):
        """RawOcrItem 에 candidates 필드 존재, 기본값 빈 리스트."""
        from app.domain.ocr import RawOcrItem
        item = RawOcrItem(name_raw="타이레놀정", confidence=Decimal("0.9"))
        assert hasattr(item, "candidates")
        assert item.candidates == []

    def test_candidates_accepts_list_of_strings(self):
        """candidates 가 문자열 리스트를 수용."""
        from app.domain.ocr import RawOcrItem
        item = RawOcrItem(
            name_raw="쎌박타민정",
            confidence=Decimal("0.6"),
            candidates=["썰박타민정", "쎄박타민정", "설박타민정"],
        )
        assert len(item.candidates) == 3
        assert "썰박타민정" in item.candidates

    def test_candidates_serializes_in_model_dump(self):
        """model_dump() 에 candidates 포함."""
        from app.domain.ocr import RawOcrItem
        item = RawOcrItem(
            name_raw="엘리버드정",
            confidence=Decimal("0.5"),
            candidates=["세티리진정", "오로파타딘정"],
        )
        dumped = item.model_dump()
        assert "candidates" in dumped
        assert dumped["candidates"] == ["세티리진정", "오로파타딘정"]


class TestGeminiVisionMimeType:
    def test_png_bytes_use_image_png_mime(self):
        """PNG 헤더(\\x89PNG) → image/png MIME type."""
        from app.rag.ocr.vision import GeminiVisionAdapter, _detect_mime_type
        png_bytes = b"\x89PNG\r\n\x1a\n" + b"\x00" * 100
        assert _detect_mime_type(png_bytes) == "image/png"

    def test_jpeg_bytes_use_image_jpeg_mime(self):
        """JPEG 헤더(\\xff\\xd8) → image/jpeg MIME type."""
        from app.rag.ocr.vision import GeminiVisionAdapter, _detect_mime_type
        jpeg_bytes = b"\xff\xd8\xff\xe0" + b"\x00" * 100
        assert _detect_mime_type(jpeg_bytes) == "image/jpeg"

    def test_unknown_bytes_default_jpeg(self):
        """알 수 없는 헤더 → image/jpeg 기본값."""
        from app.rag.ocr.vision import _detect_mime_type
        assert _detect_mime_type(b"\x00\x01\x02\x03") == "image/jpeg"
