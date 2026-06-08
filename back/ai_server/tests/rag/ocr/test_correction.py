"""
Tier 3 OcrCorrectionAdapter 단위 테스트 — TDD RED

cascade 실패 시 Gemini Flash 로 OCR 오인식 보정 top-3 추정 후 DB 재매칭.
"""
from __future__ import annotations

from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest


class TestOcrCorrectionAdapter:
    def test_correction_adapter_importable(self):
        """OcrCorrectionAdapter 클래스가 correction.py 에 존재."""
        from app.rag.ocr.correction import OcrCorrectionAdapter
        assert OcrCorrectionAdapter is not None

    def test_correction_adapter_has_correct_method(self):
        """correct(name_raw) 비동기 메서드 존재."""
        import inspect
        from app.rag.ocr.correction import OcrCorrectionAdapter

        assert hasattr(OcrCorrectionAdapter, "correct")
        assert inspect.iscoroutinefunction(OcrCorrectionAdapter.correct)

    @pytest.mark.asyncio
    async def test_correct_returns_list_of_strings(self):
        """correct() 는 str 리스트를 반환 (top-3 약품명 후보)."""
        from app.rag.ocr.correction import OcrCorrectionAdapter

        mock_llm = AsyncMock()
        mock_llm.ainvoke.return_value = MagicMock(
            content='{"candidates": ["썰박타민정500밀리그램", "쎄박타민정", "셀박타민정"]}'
        )

        adapter = OcrCorrectionAdapter(llm=mock_llm)
        result = await adapter.correct("쎌박타민정")

        assert isinstance(result, list)
        assert all(isinstance(c, str) for c in result)

    @pytest.mark.asyncio
    async def test_correct_returns_empty_on_llm_error(self):
        """LLM 호출 실패 시 빈 리스트 반환 (cascade 계속 가능)."""
        from app.rag.ocr.correction import OcrCorrectionAdapter

        mock_llm = AsyncMock()
        mock_llm.ainvoke.side_effect = Exception("LLM timeout")

        adapter = OcrCorrectionAdapter(llm=mock_llm)
        result = await adapter.correct("알 수 없는 약")

        assert result == []

    @pytest.mark.asyncio
    async def test_correct_returns_empty_on_malformed_response(self):
        """LLM 응답 파싱 실패 시 빈 리스트 반환."""
        from app.rag.ocr.correction import OcrCorrectionAdapter

        mock_llm = AsyncMock()
        mock_llm.ainvoke.return_value = MagicMock(content="뭔가 이상한 텍스트")

        adapter = OcrCorrectionAdapter(llm=mock_llm)
        result = await adapter.correct("에치콘정")

        assert result == []

    def test_correction_prompt_includes_mfds_reference(self):
        """보정 프롬프트에 식약처 출처 언급 포함 (medical-safety)."""
        from app.rag.ocr.correction import CORRECTION_PROMPT_TEMPLATE
        assert "식약처" in CORRECTION_PROMPT_TEMPLATE or "식품의약품안전처" in CORRECTION_PROMPT_TEMPLATE
