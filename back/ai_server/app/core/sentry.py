"""Sentry 초기화 + 의료 민감 데이터 스크러버.

★ DSN 빈값이면 init 을 호출하지 않는다 (로컬 OFF).
★ send_default_pii=False — IP, 사용자 에이전트 등 PII 미전송.
★ before_send 로 request.data 제거 — 처방전 텍스트·약품명 포함 가능성 차단.
"""
from __future__ import annotations

import logging

import sentry_sdk

logger = logging.getLogger(__name__)


def scrub_medical_data(event: dict, hint: dict) -> dict | None:
    """request.data(처방 텍스트·약품명 포함 가능)를 Sentry 이벤트에서 제거한다."""
    if "request" in event:
        event["request"].pop("data", None)
    return event


def init_sentry(dsn: str | None, environment: str) -> None:
    """Sentry SDK 초기화. DSN 빈값이면 skip (로컬 개발 환경 OFF)."""
    if not dsn:
        logger.debug("SENTRY_DSN 빈값 — Sentry 비활성")
        return

    sentry_sdk.init(
        dsn=dsn,
        environment=environment,
        traces_sample_rate=0.1,
        send_default_pii=False,
        before_send=scrub_medical_data,
    )
    logger.info("Sentry 초기화 완료 environment=%s", environment)
