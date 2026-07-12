"""
T-AI-OCR-LATENCY-30S (후속) — pill_identify 기본 OFF TDD

사용자 결정(2026-07-11): pill_identify 병렬화 대신 기본 비활성.
이름 미인식 약은 기존 MANUAL/NONE 경로(FE '검색으로 추가' 유도) 그대로,
코드는 삭제하지 않고 플래그로만 스킵 — 필요 시 언제든 재활성 가능.
"""
from __future__ import annotations


class TestPillIdentifyEnabledDefault:
    def test_defaults_to_false(self):
        from app.core.config import Settings

        s = Settings()
        assert s.pill_identify_enabled is False

    def test_explicit_true_still_enables(self):
        """필요 시 명시적으로 켤 수 있음 — 코드 삭제 아님, 플래그 토글만."""
        from app.core.config import Settings

        s = Settings(PILL_IDENTIFY_ENABLED="true")
        assert s.pill_identify_enabled is True

    def test_explicit_false_stays_disabled(self):
        from app.core.config import Settings

        s = Settings(PILL_IDENTIFY_ENABLED="false")
        assert s.pill_identify_enabled is False
