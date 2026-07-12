"""
T-AI-OCR-LATENCY-30S (후속) — pill_identify 기본 OFF 팩토리 배선(main._build_ocr_service) TDD

사용자 결정: PillIdentifyAdapter 코드는 유지, settings.pill_identify_enabled 플래그로만 스킵.
기존 PillIdentifyAdapter 단위 테스트(test_pill_identify.py)는 어댑터를 직접 생성해 플래그와
무관하게 그대로 유효 — 본 파일은 "플래그 on 이어야 배선된다"는 팩토리 레벨 계약만 검증한다.
"""
from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import MagicMock, patch


def _settings(pill_identify_enabled: bool):
    return SimpleNamespace(
        drug_matcher_impl="rrf",
        gemini_key_list=["AIzaTESTKEY1234"],
        gemini_model="gemini-2.5-flash",
        ocr_vision_variant="flash",
        ocr_fewshot_enabled=False,
        ocr_preprocess_enabled=False,
        pill_identify_enabled=pill_identify_enabled,
    )


def _build(settings) -> "OcrPrescriptionService":
    from app.main import _build_ocr_service

    with patch("app.rag.ocr.rrf_factory.build_rrf_matcher", return_value=MagicMock()):
        return _build_ocr_service(pool=MagicMock(), retriever=MagicMock(), settings=settings)


class TestPillIdentifyFactoryWiring:
    def test_default_off_wires_pill_identifier_none(self):
        service = _build(_settings(pill_identify_enabled=False))
        assert service._pill_identifier is None

    def test_explicit_on_wires_real_pill_identify_adapter(self):
        from app.rag.ocr.pill_identify import PillIdentifyAdapter

        service = _build(_settings(pill_identify_enabled=True))
        assert isinstance(service._pill_identifier, PillIdentifyAdapter)
