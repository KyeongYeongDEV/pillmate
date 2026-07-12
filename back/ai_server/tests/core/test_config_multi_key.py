"""Settings.gemini_api_key single-key TDD — T-BE-GEMINI-KEY-SIMPLIFY."""
from __future__ import annotations


class TestGeminiApiKey:
    def test_gemini_api_key_field_exists_as_string(self):
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY="AIzaSingleKey")
        assert isinstance(s.gemini_api_key, str)
        assert s.gemini_api_key == "AIzaSingleKey"

    def test_gemini_api_key_defaults_to_empty(self):
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY="")
        assert s.gemini_api_key == ""

    def test_legacy_key1_key2_no_longer_supported(self):
        """KEY1/KEY2 필드 및 gemini_keys property 제거 — attribute 없음."""
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY="AIzaOnly")
        assert not hasattr(s, "gemini_api_key1")
        assert not hasattr(s, "gemini_api_key2")
        assert not hasattr(s, "gemini_keys")

    def test_gemini_key_list_wraps_nonempty_key(self):
        """T-BE-AUDIT-P1-FIXES Fix2 — 유효 키는 단일 원소 리스트."""
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY="AIzaValid")
        assert s.gemini_key_list == ["AIzaValid"]

    def test_gemini_key_list_filters_empty_key_for_fail_fast(self):
        """빈 키 → 빈 리스트 → GeminiInvoker RuntimeError 로 부팅 조기 실패 (fail-fast 회귀 방지)."""
        from app.core.config import Settings

        s = Settings(GEMINI_API_KEY="")
        assert s.gemini_key_list == []
