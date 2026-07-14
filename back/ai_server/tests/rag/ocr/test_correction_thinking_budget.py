"""
리뷰 CONCERN (2026-07-14, 커밋 862b720) — correction 어댑터 thinking 명시 TDD

OcrCorrectionAdapter가 api_keys 로 ChatGoogleGenerativeAI 를 생성할 때 thinking_budget
미명시로 flash-lite SDK 기본(thinking OFF)에 암묵 의존 중이었다. vision.py의
VISION_THINKING_BUDGET=0 선례처럼 명시적으로 thinking_budget=0 을 넣어 모델 기본이
바뀌어도 8s 타임아웃 안에서 동작을 보장한다.
"""
from __future__ import annotations


class TestCorrectionThinkingBudget:
    def test_api_keys_path_builds_llms_with_thinking_disabled(self):
        from app.rag.ocr.correction import OcrCorrectionAdapter

        adapter = OcrCorrectionAdapter(api_keys=["AIzaTESTKEY1", "AIzaTESTKEY2"])

        assert len(adapter._llms) == 2
        for llm in adapter._llms:
            assert llm.thinking_budget == 0
