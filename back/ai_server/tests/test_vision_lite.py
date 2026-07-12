"""
T-OCR-VISION-VARIANT — flash-lite 전용 GeminiVisionLiteAdapter TDD

flash-lite 가 frequency/duration_days/confidence 를 null 로 정직 반환해도
RawOcrItemLite(Optional) 스키마로 파싱 → 도메인 RawOcrItem 으로 안전 변환.
기존 flash(RawOcrItem) 무변경 회귀는 test_vision_fallback.py 가 담당.
"""
from __future__ import annotations

from decimal import Decimal

import pytest
from unittest.mock import AsyncMock, MagicMock


def _tiny_jpeg() -> bytes:
    import cv2
    import numpy as np

    img = np.zeros((32, 32, 3), dtype=np.uint8)
    _, buf = cv2.imencode(".jpg", img)
    return bytes(buf)


class TestRawOcrItemLiteSchema:
    def test_accepts_null_frequency_and_duration(self):
        from app.rag.ocr.vision_lite import RawOcrItemLite
        item = RawOcrItemLite(name_raw="타이레놀정", confidence=Decimal("0.9"),
                              frequency=None, duration_days=None)
        assert item.frequency is None
        assert item.duration_days is None

    def test_accepts_null_confidence(self):
        from app.rag.ocr.vision_lite import RawOcrItemLite
        item = RawOcrItemLite(name_raw="타이레놀정", confidence=None)
        assert item.confidence is None

    def test_accepts_integer_values(self):
        from app.rag.ocr.vision_lite import RawOcrItemLite
        item = RawOcrItemLite(name_raw="타이레놀정", confidence=Decimal("0.95"),
                              frequency=3, duration_days=7)
        assert item.frequency == 3
        assert item.duration_days == 7


class TestToRawConversion:
    def test_null_frequency_becomes_default(self):
        from app.domain.ocr import DEFAULT_FREQUENCY
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter, RawOcrItemLite
        raw = GeminiVisionLiteAdapter._to_raw(
            RawOcrItemLite(name_raw="약", confidence=Decimal("0.9"), frequency=None))
        assert raw.frequency == DEFAULT_FREQUENCY

    def test_null_confidence_becomes_safe_default(self):
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter, LITE_NULL_CONFIDENCE, RawOcrItemLite
        raw = GeminiVisionLiteAdapter._to_raw(
            RawOcrItemLite(name_raw="약", confidence=None))
        assert raw.confidence == LITE_NULL_CONFIDENCE
        assert raw.confidence < Decimal("0.7")  # auto 임계치 미만 → MANUAL 유도 (medical-safety)

    def test_preserves_present_values(self):
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter, RawOcrItemLite
        raw = GeminiVisionLiteAdapter._to_raw(RawOcrItemLite(
            name_raw="타이레놀정", confidence=Decimal("0.92"),
            frequency=2, duration_days=5, dose_amount=Decimal("1.0"), dose_unit="정"))
        assert raw.frequency == 2
        assert raw.duration_days == 5
        assert raw.confidence == Decimal("0.92")
        assert raw.dose_unit == "정"


class TestGeminiVisionLiteAdapterExtract:
    @pytest.mark.asyncio
    async def test_extract_parses_lite_null_response(self):
        """flash-lite 가 frequency/duration_days null 반환 → 504 없이 정상 파싱 (핵심 회귀 방지)."""
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter

        lite_response = (
            '{"items": [{"name_raw": "타이레놀정", "confidence": 0.95, '
            '"frequency": null, "duration_days": null}]}'
        )
        llm = AsyncMock()
        llm.ainvoke.return_value = MagicMock(content=lite_response)

        adapter = GeminiVisionLiteAdapter(_llms=[llm])
        items = await adapter.extract(_tiny_jpeg())

        assert len(items) == 1
        assert items[0].name_raw == "타이레놀정"
        assert items[0].frequency == 3  # null → DEFAULT_FREQUENCY
        assert items[0].duration_days is None

    @pytest.mark.asyncio
    async def test_extract_parses_integer_response(self):
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter

        response = (
            '{"items": [{"name_raw": "게보린정", "confidence": 0.9, '
            '"frequency": 3, "duration_days": 7}]}'
        )
        llm = AsyncMock()
        llm.ainvoke.return_value = MagicMock(content=response)

        adapter = GeminiVisionLiteAdapter(_llms=[llm])
        items = await adapter.extract(_tiny_jpeg())

        assert items[0].frequency == 3
        assert items[0].duration_days == 7

    @pytest.mark.asyncio
    async def test_extract_empty_items(self):
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter

        llm = AsyncMock()
        llm.ainvoke.return_value = MagicMock(content='{"items": []}')

        adapter = GeminiVisionLiteAdapter(_llms=[llm])
        items = await adapter.extract(_tiny_jpeg())

        assert items == []

    @pytest.mark.asyncio
    async def test_extract_partial_recovery_drops_broken_item_keeps_survivor(self):
        """T-AI-OCR-LATENCY-30S — 아이템 하나 dose_amount 타입오염이어도 전체 재호출 없이 부분복구."""
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter

        # dose_amount 가 숫자로 해석 불가한 문자열 → strict 파싱 실패 → lenient 복구로 None 강등, 생존
        broken_response = (
            '{"items": [{"name_raw": "타이레놀정", "confidence": 0.9, "dose_amount": "모름"}]}'
        )
        llm = AsyncMock()
        llm.ainvoke.return_value = MagicMock(content=broken_response)

        adapter = GeminiVisionLiteAdapter(_llms=[llm])
        items = await adapter.extract(_tiny_jpeg())

        assert len(items) == 1
        assert items[0].name_raw == "타이레놀정"
        assert items[0].dose_amount is None
        assert llm.ainvoke.await_count == 1  # 재호출 없이 부분복구로 해결


class TestVisionVariantFactory:
    def _settings(self, model: str, variant: str = "auto"):
        from types import SimpleNamespace
        return SimpleNamespace(
            gemini_model=model,
            ocr_vision_variant=variant,
            gemini_key_list=["AIzaTESTKEY1234"],
            ocr_fewshot_enabled=False,
        )

    def test_auto_selects_lite_for_flash_lite_model(self):
        from app.main import _resolve_vision_variant
        assert _resolve_vision_variant(self._settings("gemini-2.5-flash-lite")) == "lite"

    def test_auto_selects_flash_for_flash_model(self):
        from app.main import _resolve_vision_variant
        assert _resolve_vision_variant(self._settings("gemini-2.5-flash")) == "flash"

    def test_env_override_forces_flash(self):
        from app.main import _resolve_vision_variant
        assert _resolve_vision_variant(
            self._settings("gemini-2.5-flash-lite", variant="flash")) == "flash"

    def test_build_vision_returns_lite_adapter(self):
        from app.main import _build_vision
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter
        adapter = _build_vision(self._settings("gemini-2.5-flash-lite"))
        assert isinstance(adapter, GeminiVisionLiteAdapter)

    def test_build_vision_returns_flash_adapter(self):
        from app.main import _build_vision
        from app.rag.ocr.vision import GeminiVisionAdapter
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter
        adapter = _build_vision(self._settings("gemini-2.5-flash"))
        assert isinstance(adapter, GeminiVisionAdapter)
        assert not isinstance(adapter, GeminiVisionLiteAdapter)
