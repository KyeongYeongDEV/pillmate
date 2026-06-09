"""
OcrCorrectionAdapter 다중 key fallback TDD

T-AI-OCR-MULTI-KEY-FALLBACK: Tier 3 correction adapter 도 key rotation 적용
"""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock

from google.genai import errors as google_errors


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


_VALID_CORRECTION_RESPONSE = '{"candidates": ["썰박타민정500밀리그램", "쎄박타민정"]}'


class TestOcrCorrectionAdapterKeyRotation:
    @pytest.mark.asyncio
    async def test_primary_429_falls_back_to_secondary(self):
        """primary 429 ClientError → secondary 로 retry 성공."""
        from app.rag.ocr.correction import OcrCorrectionAdapter

        primary = AsyncMock()
        primary.ainvoke.side_effect = _rate_limited_429()

        secondary = AsyncMock()
        secondary.ainvoke.return_value = MagicMock(content=_VALID_CORRECTION_RESPONSE)

        adapter = OcrCorrectionAdapter(_llms=[primary, secondary])
        result = await adapter.correct("쎌박타민정")

        assert primary.ainvoke.called
        assert secondary.ainvoke.called
        assert isinstance(result, list)

    @pytest.mark.asyncio
    async def test_all_keys_429_returns_empty_list(self):
        """모든 key 429 → 빈 리스트 반환 (cascade 계속 가능)."""
        from app.rag.ocr.correction import OcrCorrectionAdapter

        primary = AsyncMock()
        primary.ainvoke.side_effect = _rate_limited_429()

        secondary = AsyncMock()
        secondary.ainvoke.side_effect = _rate_limited_429()

        adapter = OcrCorrectionAdapter(_llms=[primary, secondary])
        result = await adapter.correct("알 수 없는 약")

        assert result == []

    @pytest.mark.asyncio
    async def test_503_triggers_rotation(self):
        """503 ServerError → secondary retry."""
        from app.rag.ocr.correction import OcrCorrectionAdapter

        primary = AsyncMock()
        primary.ainvoke.side_effect = _unavailable_503()

        secondary = AsyncMock()
        secondary.ainvoke.return_value = MagicMock(content=_VALID_CORRECTION_RESPONSE)

        adapter = OcrCorrectionAdapter(_llms=[primary, secondary])
        result = await adapter.correct("에치콘정")

        assert secondary.ainvoke.called
        assert isinstance(result, list)

    @pytest.mark.asyncio
    async def test_single_key_mode_backward_compat(self):
        """기존 llm= 파라미터 backward compat 유지."""
        from app.rag.ocr.correction import OcrCorrectionAdapter

        mock_llm = AsyncMock()
        mock_llm.ainvoke.return_value = MagicMock(content=_VALID_CORRECTION_RESPONSE)

        adapter = OcrCorrectionAdapter(llm=mock_llm)
        result = await adapter.correct("타이레놀정")

        assert isinstance(result, list)
        assert mock_llm.ainvoke.called
