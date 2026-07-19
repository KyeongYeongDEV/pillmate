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
    """Sentry SDK 초기화. DSN 빈값이면 skip (로컬 개발 환경 OFF).

    DSN 형식이 잘못된 경우(BadDsn 등)에도 예외를 전파하지 않고 Sentry 를
    비활성화한 채 앱 기동을 계속한다 — 설정 오류로 서비스 전체가 죽으면 안 된다.
    """
    if not dsn:
        logger.debug("SENTRY_DSN 빈값 — Sentry 비활성")
        return

    try:
        sentry_sdk.init(
            dsn=dsn,
            environment=environment,
            traces_sample_rate=0.1,
            send_default_pii=False,
            before_send=scrub_medical_data,
        )
    except Exception as exc:
        logger.warning(
            "Sentry init 실패 — DSN 오류로 비활성화: %s", exc.__class__.__name__
        )
        return
    logger.info("Sentry 초기화 완료 environment=%s", environment)
