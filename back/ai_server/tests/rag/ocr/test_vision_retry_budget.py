"""
T-AI-OCR-LATENCY-30S — GeminiVisionAdapter 재시도/관측 로그 TDD

2026-07-11 사용자 최종 결정: 시간 기반 예산제(총예산/잔여예산 가드) 전부 폐기.
남기는 안전망은 단 하나 — per-call 150s(BE read timeout 170s 안쪽, 순수 행 방지용).
재시도는 에러(파싱실패/API에러/타임아웃) 시 정확히 1회만, 타이머·백오프 없이 즉시.
기존 429/503 key rotation 동작(test_vision_fallback.py)은 무변경.
"""
from __future__ import annotations

import asyncio
import json
import logging

import pytest
from unittest.mock import AsyncMock, MagicMock

from app.exceptions import VisionBusyError


def _tiny_jpeg() -> bytes:
    import cv2
    import numpy as np

    img = np.zeros((32, 32, 3), dtype=np.uint8)
    _, buf = cv2.imencode(".jpg", img)
    return bytes(buf)


_VALID_RESPONSE = '{"items": [{"name_raw": "타이레놀정", "confidence": 0.95}]}'
# name_raw 자체가 없어 부분복구(lenient)로도 살릴 아이템이 0개 → 진짜 파싱실패 유발
_BROKEN_RESPONSE = '{"items": [{"confidence": 0.9}]}'


class TestSingleErrorRetry:
    """에러(파싱실패/API에러) 시 타이머 없이 정확히 1회만 즉시 재시도."""

    @pytest.mark.asyncio
    async def test_parse_failure_retries_once_then_succeeds(self):
        from app.rag.ocr.vision import GeminiVisionAdapter

        llm = AsyncMock()
        llm.ainvoke.side_effect = [
            MagicMock(content=_BROKEN_RESPONSE),
            MagicMock(content=_VALID_RESPONSE),
        ]
        adapter = GeminiVisionAdapter(_llms=[llm])

        items = await adapter.extract(_tiny_jpeg())

        assert len(items) == 1
        assert items[0].name_raw == "타이레놀정"
        assert llm.ainvoke.await_count == 2

    @pytest.mark.asyncio
    async def test_parse_failure_twice_raises_busy_error_after_exactly_one_retry(self):
        from app.rag.ocr.vision import GeminiVisionAdapter

        llm = AsyncMock()
        llm.ainvoke.return_value = MagicMock(content=_BROKEN_RESPONSE)
        adapter = GeminiVisionAdapter(_llms=[llm])

        with pytest.raises(VisionBusyError):
            await adapter.extract(_tiny_jpeg())

        assert llm.ainvoke.await_count == 2  # 최초 1 + 재시도 1, 그 이상 없음

    @pytest.mark.asyncio
    async def test_generic_exception_retries_once_then_busy_error(self):
        from app.rag.ocr.vision import GeminiVisionAdapter

        llm = AsyncMock()
        llm.ainvoke.side_effect = RuntimeError("transient upstream error")
        adapter = GeminiVisionAdapter(_llms=[llm])

        with pytest.raises(VisionBusyError):
            await adapter.extract(_tiny_jpeg())

        assert llm.ainvoke.await_count == 2

    @pytest.mark.asyncio
    async def test_retry_has_no_backoff_delay(self):
        """타이머 재시도 금지 — 재시도 사이 인위적 대기가 없어야 한다(즉시 재시도)."""
        import time as time_module

        from app.rag.ocr.vision import GeminiVisionAdapter

        llm = AsyncMock()
        llm.ainvoke.side_effect = [
            RuntimeError("transient"),
            MagicMock(content=_VALID_RESPONSE),
        ]
        adapter = GeminiVisionAdapter(_llms=[llm])

        t0 = time_module.monotonic()
        await adapter.extract(_tiny_jpeg())
        elapsed = time_module.monotonic() - t0

        assert elapsed < 0.2  # 백오프 없음 — 거의 즉시 완료


class TestPerCallSafetyNet:
    @pytest.mark.asyncio
    async def test_call_exceeding_safety_net_times_out_and_retries_once(self):
        from app.rag.ocr.vision import GeminiVisionAdapter

        call_count = 0

        async def flaky_ainvoke(_messages):
            nonlocal call_count
            call_count += 1
            if call_count == 1:
                await asyncio.sleep(0.05)
                raise asyncio.TimeoutError()
            return MagicMock(content=_VALID_RESPONSE)

        llm = MagicMock()
        llm.ainvoke = flaky_ainvoke
        adapter = GeminiVisionAdapter(_llms=[llm], timeout_sec=0.01)

        items = await adapter.extract(_tiny_jpeg())

        assert len(items) == 1
        assert call_count == 2

    @pytest.mark.asyncio
    async def test_default_safety_net_is_150_seconds(self):
        from app.rag.ocr.vision import VISION_TIMEOUT_SEC, GeminiVisionAdapter

        llm = AsyncMock()
        llm.ainvoke.return_value = MagicMock(content=_VALID_RESPONSE)
        adapter = GeminiVisionAdapter(_llms=[llm])

        assert VISION_TIMEOUT_SEC == 150.0
        assert adapter._timeout == 150.0


class TestRateLimitRotationUnchanged:
    @pytest.mark.asyncio
    async def test_single_key_429_still_raises_vision_invocation_error_immediately(self):
        """기존 test_vision_fallback.py 계약 유지 — rotation 대상 키 없으면 즉시 VisionInvocationError."""
        from app.exceptions import VisionInvocationError
        from google.genai import errors as google_errors

        from app.rag.ocr.vision import GeminiVisionAdapter

        single = AsyncMock()
        single.ainvoke.side_effect = google_errors.ClientError(
            429, {"error": {"code": 429, "message": "RPD exceeded", "status": "RESOURCE_EXHAUSTED"}}
        )
        adapter = GeminiVisionAdapter(_llms=[single])

        with pytest.raises(VisionInvocationError):
            await adapter.extract(_tiny_jpeg())

        assert single.ainvoke.await_count == 1  # 재시도 없이 즉시 실패(에러 재시도 대상 아님)


class TestAttemptStructuredLog:
    @pytest.mark.asyncio
    async def test_success_logs_ok_outcome_with_schema(self, caplog):
        from app.rag.ocr.vision import GeminiVisionAdapter

        llm = AsyncMock()
        llm.ainvoke.return_value = MagicMock(content=_VALID_RESPONSE)
        adapter = GeminiVisionAdapter(_llms=[llm], model="gemini-2.5-flash")

        with caplog.at_level(logging.INFO, logger="app.rag.ocr.vision"):
            await adapter.extract(_tiny_jpeg())

        entries = [json.loads(r.message) for r in caplog.records if r.message.startswith("{")]
        attempt_logs = [e for e in entries if e.get("event") == "vision_attempt"]
        assert len(attempt_logs) == 1
        log = attempt_logs[0]
        assert log["attempt"] == 1
        assert log["outcome"] == "ok"
        assert log["error_class"] is None
        assert log["model"] == "gemini-2.5-flash"
        assert isinstance(log["elapsed_ms"], int)

    @pytest.mark.asyncio
    async def test_parse_fail_then_success_logs_both_attempts(self, caplog):
        from app.rag.ocr.vision import GeminiVisionAdapter

        llm = AsyncMock()
        llm.ainvoke.side_effect = [
            MagicMock(content=_BROKEN_RESPONSE),
            MagicMock(content=_VALID_RESPONSE),
        ]
        adapter = GeminiVisionAdapter(_llms=[llm])

        with caplog.at_level(logging.INFO, logger="app.rag.ocr.vision"):
            await adapter.extract(_tiny_jpeg())

        entries = [json.loads(r.message) for r in caplog.records if r.message.startswith("{")]
        attempt_logs = [e for e in entries if e.get("event") == "vision_attempt"]
        assert len(attempt_logs) == 2
        assert attempt_logs[0]["outcome"] == "parse_fail"
        assert attempt_logs[0]["attempt"] == 1
        assert attempt_logs[1]["outcome"] == "ok"
        assert attempt_logs[1]["attempt"] == 2
