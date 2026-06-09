"""Settings.gemini_keys property TDD RED — T-AI-OCR-MULTI-KEY-FALLBACK."""
from __future__ import annotations

import pytest


class TestGeminiKeysProperty:
    def test_gemini_keys_returns_key1_when_only_key1_set(self):
        """GEMINI_API_KEY1 만 설정 시 [KEY1] 반환 (KEY2/legacy 명시적 빈값)."""
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY1="AIzaKey1Primary", GEMINI_API_KEY2="", GEMINI_API_KEY="")
        assert s.gemini_keys == ["AIzaKey1Primary"]

    def test_gemini_keys_returns_key1_and_key2_when_both_set(self):
        """GEMINI_API_KEY1 + GEMINI_API_KEY2 설정 시 [KEY1, KEY2] 반환."""
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY1="AIzaKey1Primary", GEMINI_API_KEY2="AIzaKey2Secondary")
        assert s.gemini_keys == ["AIzaKey1Primary", "AIzaKey2Secondary"]

    def test_gemini_keys_legacy_fallback_when_no_key1(self):
        """KEY1 미설정 → GEMINI_API_KEY (legacy) 를 primary 로 사용."""
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY1="", GEMINI_API_KEY2="", GEMINI_API_KEY="AIzaLegacyOnly")
        assert s.gemini_keys == ["AIzaLegacyOnly"]

    def test_gemini_keys_filters_empty(self):
        """모든 key 빈 값이면 빈 리스트."""
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY1="", GEMINI_API_KEY2="", GEMINI_API_KEY="")
        assert s.gemini_keys == []

    def test_gemini_api_key1_field_exists_as_string(self):
        """GEMINI_API_KEY1 필드가 str 타입으로 존재."""
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY1="AIzaTestKey1", GEMINI_API_KEY2="", GEMINI_API_KEY="")
        assert isinstance(s.gemini_api_key1, str)
        assert s.gemini_api_key1 == "AIzaTestKey1"

    def test_gemini_api_key2_field_exists_as_string(self):
        """GEMINI_API_KEY2 필드가 str 타입으로 존재."""
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY1="", GEMINI_API_KEY2="AIzaTestKey2", GEMINI_API_KEY="")
        assert isinstance(s.gemini_api_key2, str)
        assert s.gemini_api_key2 == "AIzaTestKey2"

    def test_key1_takes_priority_over_legacy(self):
        """KEY1 + GEMINI_API_KEY 동시 설정 → KEY1 만 primary, legacy 중복 제외."""
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY1="AIzaNew1", GEMINI_API_KEY="AIzaOldLegacy")
        assert "AIzaNew1" in s.gemini_keys
        assert "AIzaOldLegacy" not in s.gemini_keys

    def test_key2_plus_legacy_when_no_key1(self):
        """KEY1 없고 GEMINI_API_KEY + KEY2 → [legacy, KEY2] 순서."""
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY1="", GEMINI_API_KEY="AIzaLegacy", GEMINI_API_KEY2="AIzaKey2")
        keys = s.gemini_keys
        assert keys[0] == "AIzaLegacy"
        assert keys[1] == "AIzaKey2"
