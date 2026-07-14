"""
T-AI-OCR-LATENCY-30S (후속) — OcrCorrectionAdapter per-call timeout + 구조화 로그 TDD

실측: 이름 못 읽은 알약당 correction(Tier 3) Gemini 호출이 순차 15~37s 씩 걸려
total_elapsed_ms=99728 까지 늘어남. per-call 20s 상한으로 해당 알약만 스킵(빈 리스트,
기존 에러 시 빈 리스트 반환 계약과 동일)하고 전체 실패는 안 나게 한다.
"""
from __future__ import annotations

import asyncio
import json
import logging
import time

import pytest
from unittest.mock import AsyncMock, MagicMock


_VALID_RESPONSE = '{"candidates": ["타이레놀정500밀리그램"]}'


class TestCorrectionPerCallTimeout:
    @pytest.mark.asyncio
    async def test_slow_call_exceeding_timeout_returns_empty_list_fast(self):
        from app.rag.ocr.correction import OcrCorrectionAdapter

        async def slow_ainvoke(_messages):
            await asyncio.sleep(0.5)
            return MagicMock(content=_VALID_RESPONSE)

        llm = MagicMock()
        llm.ainvoke = slow_ainvoke
        adapter = OcrCorrectionAdapter(_llms=[llm], timeout_sec=0.05)

        start = time.monotonic()
        result = await adapter.correct("알수없는약")
        elapsed = time.monotonic() - start

        assert result == []
        assert elapsed < 0.3  # per-call timeout(0.05s) 근처에서 fast-return, 0.5s 다 안 기다림

    @pytest.mark.asyncio
    async def test_fast_call_within_timeout_still_succeeds(self):
        from app.rag.ocr.correction import OcrCorrectionAdapter

        llm = AsyncMock()
        llm.ainvoke.return_value = MagicMock(content=_VALID_RESPONSE)
        adapter = OcrCorrectionAdapter(_llms=[llm], timeout_sec=20.0)

        result = await adapter.correct("타이레놀")

        assert result == ["타이레놀정500밀리그램"]

    @pytest.mark.asyncio
    async def test_default_timeout_is_8_seconds(self):
        from app.rag.ocr.correction import CORRECTION_TIMEOUT_SEC, OcrCorrectionAdapter

        llm = AsyncMock()
        llm.ainvoke.return_value = MagicMock(content=_VALID_RESPONSE)
        adapter = OcrCorrectionAdapter(_llms=[llm])

        assert CORRECTION_TIMEOUT_SEC == 8.0
        assert adapter._timeout == 8.0


class TestCorrectionStructuredLog:
    @pytest.mark.asyncio
    async def test_success_logs_ok_outcome(self, caplog):
        from app.rag.ocr.correction import OcrCorrectionAdapter

        llm = AsyncMock()
        llm.ainvoke.return_value = MagicMock(content=_VALID_RESPONSE)
        adapter = OcrCorrectionAdapter(_llms=[llm])

        with caplog.at_level(logging.INFO, logger="app.rag.ocr.correction"):
            await adapter.correct("타이레놀")

        entries = [json.loads(r.message) for r in caplog.records if r.message.startswith("{")]
        logs = [e for e in entries if e.get("event") == "correction_attempt"]
        assert len(logs) == 1
        assert logs[0]["outcome"] == "ok"
        assert logs[0]["error_class"] is None
        assert isinstance(logs[0]["elapsed_ms"], int)

    @pytest.mark.asyncio
    async def test_timeout_logs_timeout_outcome(self, caplog):
        from app.rag.ocr.correction import OcrCorrectionAdapter

        async def slow_ainvoke(_messages):
            await asyncio.sleep(0.3)
            return MagicMock(content=_VALID_RESPONSE)

        llm = MagicMock()
        llm.ainvoke = slow_ainvoke
        adapter = OcrCorrectionAdapter(_llms=[llm], timeout_sec=0.02)

        with caplog.at_level(logging.INFO, logger="app.rag.ocr.correction"):
            await adapter.correct("알수없는약")

        entries = [json.loads(r.message) for r in caplog.records if r.message.startswith("{")]
        logs = [e for e in entries if e.get("event") == "correction_attempt"]
        assert len(logs) == 1
        assert logs[0]["outcome"] == "timeout"
        assert logs[0]["error_class"] == "TimeoutError"


class TestCorrectionExistingContractsPreserved:
    @pytest.mark.asyncio
    async def test_error_still_returns_empty_list(self):
        """기존 계약: LLM 예외 시 빈 리스트 (cascade 계속 가능) — 변경 없음."""
        from app.rag.ocr.correction import OcrCorrectionAdapter

        llm = AsyncMock()
        llm.ainvoke.side_effect = RuntimeError("boom")
        adapter = OcrCorrectionAdapter(_llms=[llm])

        result = await adapter.correct("이름")

        assert result == []
