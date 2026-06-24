"""Sentry 설정 검증:
1. DSN 빈값이면 sentry_sdk.init 을 호출하지 않는다.
2. before_send 스크러버가 request.data(처방 텍스트·약품명 포함 가능)를 제거한다.
3. send_default_pii=False 설정이 init 호출 시 반드시 포함된다.
"""
import pytest
from unittest.mock import MagicMock, patch, call


# ─── 스크러버 단위 테스트 ────────────────────────────────────────────────────

def test_scrub_removes_request_data():
    from app.core.sentry import scrub_medical_data

    event = {
        "request": {
            "url": "http://localhost/api/v1/ocr/extract",
            "method": "POST",
            "data": '{"image_key": "prescriptions/abc.jpg", "raw_text": "타이레놀 500mg"}',
            "headers": {},
        },
        "exception": {"values": [{"type": "RuntimeError"}]},
    }

    result = scrub_medical_data(event, {})

    assert result is not None
    assert "data" not in result["request"], "request.data가 제거되어야 한다 (처방 텍스트 포함 가능)"


def test_scrub_preserves_non_sensitive_fields():
    from app.core.sentry import scrub_medical_data

    event = {
        "request": {
            "url": "http://localhost/api/v1/health",
            "method": "GET",
        },
        "level": "error",
    }

    result = scrub_medical_data(event, {})

    assert result is not None
    assert result["request"]["url"] == "http://localhost/api/v1/health"
    assert result["level"] == "error"


def test_scrub_tolerates_missing_request_key():
    from app.core.sentry import scrub_medical_data

    event = {"level": "error", "message": "something went wrong"}
    result = scrub_medical_data(event, {})

    assert result is not None
    assert result["level"] == "error"


# ─── DSN 빈값 → init skip ────────────────────────────────────────────────────

def test_init_sentry_skips_when_dsn_empty():
    from app.core.sentry import init_sentry

    with patch("app.core.sentry.sentry_sdk") as mock_sdk:
        init_sentry(dsn="", environment="local")
        mock_sdk.init.assert_not_called()


def test_init_sentry_skips_when_dsn_none():
    from app.core.sentry import init_sentry

    with patch("app.core.sentry.sentry_sdk") as mock_sdk:
        init_sentry(dsn=None, environment="local")
        mock_sdk.init.assert_not_called()


def test_init_sentry_calls_init_with_send_default_pii_false():
    from app.core.sentry import init_sentry

    with patch("app.core.sentry.sentry_sdk") as mock_sdk:
        init_sentry(dsn="https://test@sentry.io/123", environment="production")
        mock_sdk.init.assert_called_once()
        _, kwargs = mock_sdk.init.call_args
        assert kwargs.get("send_default_pii") is False, "send_default_pii 는 반드시 False"


def test_init_sentry_passes_before_send_scrubber():
    from app.core.sentry import init_sentry, scrub_medical_data

    with patch("app.core.sentry.sentry_sdk") as mock_sdk:
        init_sentry(dsn="https://test@sentry.io/123", environment="production")
        _, kwargs = mock_sdk.init.call_args
        assert callable(kwargs.get("before_send")), "before_send 는 callable 이어야 한다"
