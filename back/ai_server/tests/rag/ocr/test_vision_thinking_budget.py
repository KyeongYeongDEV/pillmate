"""
T-AI-OCR-LATENCY-60S — vision LLM thinking 비활성 TDD

2026-07-14 오라클 서버 실측: gemini-2.5-flash 는 thinking 기본 ON 이라 vision 호출당
17~28s (OcrProcessed total 54~68s 의 최대 구간). OCR 추출은 구조화 전사 작업으로
thinking 불필요 → thinking_budget=0 을 모든 vision LLM 에 명시한다.
정확도는 실 처방전 A/B 로 별도 검증 (ocr-improvement-journal 기록).
"""
from __future__ import annotations


class TestVisionThinkingBudget:
    def test_flash_adapter_builds_llms_with_thinking_disabled(self):
        from app.rag.ocr.vision import GeminiVisionAdapter

        adapter = GeminiVisionAdapter(api_keys=["AIzaTESTKEY1", "AIzaTESTKEY2"])

        assert len(adapter._llms) == 2
        for llm in adapter._llms:
            assert llm.thinking_budget == 0

    def test_lite_adapter_builds_llms_with_thinking_disabled(self):
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter

        adapter = GeminiVisionLiteAdapter(api_keys=["AIzaTESTKEY1"])

        assert adapter._llms[0].thinking_budget == 0

    def test_injected_llms_bypass_construction_unchanged(self):
        """테스트 더블(_llms 직접 주입) 경로는 기존 계약 그대로 — 생성자 검증 없음."""
        from unittest.mock import AsyncMock

        from app.rag.ocr.vision import GeminiVisionAdapter

        llm = AsyncMock()
        adapter = GeminiVisionAdapter(_llms=[llm])

        assert adapter._llms == [llm]
