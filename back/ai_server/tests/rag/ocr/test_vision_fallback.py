"""
GeminiVisionAdapter 다중 key fallback TDD

T-AI-OCR-MULTI-KEY-FALLBACK: GEMINI_API_KEY1 429 → KEY2 retry
"""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock

from google.genai import errors as google_errors


def _tiny_jpeg() -> bytes:
    import cv2
    import numpy as np

    img = np.zeros((32, 32, 3), dtype=np.uint8)
    _, buf = cv2.imencode(".jpg", img)
    return bytes(buf)


def _rate_limited_429() -> google_errors.ClientError:
    return google_errors.ClientError(
        429,
        {"error": {"code": 429, "message": "RPD exceeded", "status": "RESOURCE_EXHAUSTED"}},
    )


def _unavailable_503() -> google_errors.ServerError:
    return google_errors.ServerError(
        503,
        {"error": {"code": 503, "message": "Overloaded", "status": "UNAVAILABLE"}},
    )


_VALID_OCR_RESPONSE = '{"items": [{"name_raw": "타이레놀정", "confidence": 0.95}]}'


class TestGeminiVisionKeyRotation:
    @pytest.mark.asyncio
    async def test_primary_429_falls_back_to_secondary(self):
        """primary key 429 ClientError → secondary key 로 retry 성공."""
        from app.rag.ocr.vision import GeminiVisionAdapter

        primary = AsyncMock()
        primary.ainvoke.side_effect = _rate_limited_429()

        secondary = AsyncMock()
        secondary.ainvoke.return_value = MagicMock(content=_VALID_OCR_RESPONSE)

        adapter = GeminiVisionAdapter(_llms=[primary, secondary])
        await adapter.extract(_tiny_jpeg())

        assert primary.ainvoke.called
        assert secondary.ainvoke.called

    @pytest.mark.asyncio
    async def test_all_keys_429_raises_vision_invocation_error(self):
        """primary + secondary 모두 429 → VisionInvocationError."""
        from app.exceptions import VisionInvocationError
        from app.rag.ocr.vision import GeminiVisionAdapter

        primary = AsyncMock()
        primary.ainvoke.side_effect = _rate_limited_429()

        secondary = AsyncMock()
        secondary.ainvoke.side_effect = _rate_limited_429()

        adapter = GeminiVisionAdapter(_llms=[primary, secondary])

        with pytest.raises(VisionInvocationError):
            await adapter.extract(_tiny_jpeg())

    @pytest.mark.asyncio
    async def test_single_key_mode_429_raises_without_fallback(self):
        """single key 모드 429 → VisionInvocationError (fallback 없음)."""
        from app.exceptions import VisionInvocationError
        from app.rag.ocr.vision import GeminiVisionAdapter

        single = AsyncMock()
        single.ainvoke.side_effect = _rate_limited_429()

        adapter = GeminiVisionAdapter(_llms=[single])

        with pytest.raises(VisionInvocationError):
            await adapter.extract(_tiny_jpeg())

    @pytest.mark.asyncio
    async def test_503_server_error_triggers_rotation(self):
        """503 ServerError 도 fallback key 트리거."""
        from app.rag.ocr.vision import GeminiVisionAdapter

        primary = AsyncMock()
        primary.ainvoke.side_effect = _unavailable_503()

        secondary = AsyncMock()
        secondary.ainvoke.return_value = MagicMock(content='{"items": []}')

        adapter = GeminiVisionAdapter(_llms=[primary, secondary])
        await adapter.extract(_tiny_jpeg())

        assert secondary.ainvoke.called

    @pytest.mark.asyncio
    async def test_success_on_primary_does_not_call_secondary(self):
        """primary 성공 시 secondary 는 호출 안 함."""
        from app.rag.ocr.vision import GeminiVisionAdapter

        primary = AsyncMock()
        primary.ainvoke.return_value = MagicMock(content=_VALID_OCR_RESPONSE)

        secondary = AsyncMock()

        adapter = GeminiVisionAdapter(_llms=[primary, secondary])
        await adapter.extract(_tiny_jpeg())

        assert primary.ainvoke.called
        assert not secondary.ainvoke.called

    def test_mask_key_shows_only_last_4_chars(self):
        """key 마스킹: 끝 4자리만 노출."""
        from app.rag.ocr.vision import _mask_key

        masked = _mask_key("AIzaSyABCDEFGHIJ1234")
        assert masked.endswith("1234")
        assert "AIzaSyABCDEFGHIJ" not in masked
        assert "***" in masked

    def test_mask_key_short_string(self):
        """4자 이하 key → '***' 포함."""
        from app.rag.ocr.vision import _mask_key

        assert "***" in _mask_key("abc")
