"""
T-AI-OCR-LATENCY-60S — correction(Tier 3) 지연 상한 축소 TDD

2026-07-14 오라클 서버 실측: main._build_ocr_service 가 correction 에
settings.gemini_model(=gemini-2.5-flash, thinking ON)을 override 로 넘겨
미해결 아이템당 20s 타임아웃 소진 (5건 중 4건 timeout, 매칭 기여 0).
→ (1) 어댑터 기본 모델(flash-lite) 사용으로 복원, (2) 상한 20s→8s.
"""
from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import MagicMock, patch


class TestCorrectionFactoryWiring:
    def _settings(self):
        return SimpleNamespace(
            drug_matcher_impl="rrf",
            gemini_key_list=["AIzaTESTKEY1234"],
            gemini_model="gemini-2.5-flash",
            ocr_vision_variant="flash",
            ocr_fewshot_enabled=False,
            ocr_preprocess_enabled=False,
            pill_identify_enabled=False,
        )

    def test_correction_uses_lite_model_not_vision_model(self):
        from app.main import _build_ocr_service

        with patch("app.rag.ocr.rrf_factory.build_rrf_matcher", return_value=MagicMock()):
            service = _build_ocr_service(
                pool=MagicMock(), retriever=MagicMock(), settings=self._settings()
            )

        for llm in service._correction._llms:
            assert llm.model.endswith("gemini-2.5-flash-lite")
