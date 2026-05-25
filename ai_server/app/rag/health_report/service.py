from __future__ import annotations

import json
import logging
from decimal import Decimal
from typing import Protocol

from app.domain.health_report import (
    HealthReportRequest,
    HealthReportResponse,
    InsightItem,
)

logger = logging.getLogger(__name__)

MIN_CONFIDENCE = Decimal("0.7")

SYSTEM_PROMPT = (
    "당신은 복약 관리 코치입니다. 사용자의 복약 통계를 보고 친근한 인사이트를 만듭니다.\n"
    "규칙:\n"
    "1. 모든 인사이트에 출처(source) 를 명시합니다. 식약처 의약품 효능 정보를 인용할 때는 '식약처'.\n"
    "   복용 습관 관련 일반 코칭은 'PillMate AI 분석'.\n"
    "2. 처방 변경, 약 종류 변경, 용량 변경 권유 금지 (의사 영역).\n"
    "3. 식단/생활습관/복용 시간 조정 권유만 허용.\n"
    "4. 환자 이름이나 식별정보는 사용하지 않습니다.\n"
    "5. 응답은 JSON 객체이며 키 'insights' 배열 안에 각 인사이트는 다음 필드를 갖습니다:\n"
    "   type(WARNING/RECOMMENDATION/TREND), severity(INFO/WARN/CRITICAL),\n"
    "   title(<=30자), description(<=200자), source, confidence(0.0~1.0).\n"
    "6. 응답에 '참고용입니다. 약사·의사와 상담하세요.' 의미를 description 마지막에 포함합니다.\n"
)


class LlmRunner(Protocol):
    async def invoke(self, system: str, user: str) -> str: ...


class HealthReportService:

    def __init__(self, llm: LlmRunner):
        self._llm = llm

    async def analyze(self, request: HealthReportRequest) -> HealthReportResponse:
        user_prompt = self._build_user_prompt(request)
        try:
            raw = await self._llm.invoke(SYSTEM_PROMPT, user_prompt)
        except Exception as exc:
            logger.warning("LLM analyze failed type=%s", type(exc).__name__)
            return HealthReportResponse(insights=[])
        return self._parse(raw)

    def _build_user_prompt(self, request: HealthReportRequest) -> str:
        return (
            f"기간: {request.period_type}\n"
            f"점수: {request.score} / 100\n"
            f"복약률: {request.adherence_rate}%\n"
            f"감지된 패턴: {json.dumps([p.model_dump() for p in request.patterns], ensure_ascii=False)}\n"
            f"현재 복용 중인 약: {json.dumps([d.model_dump() for d in request.drugs], ensure_ascii=False)}\n"
            "위 컨텍스트만으로 3~5개의 인사이트를 만들어 JSON 으로 응답하세요."
        )

    def _parse(self, raw: str) -> HealthReportResponse:
        cleaned = raw.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
        try:
            data = json.loads(cleaned)
        except json.JSONDecodeError:
            logger.warning("LLM JSON parse failed")
            return HealthReportResponse(insights=[])
        accepted = [
            InsightItem(**item)
            for item in data.get("insights", [])
            if self._is_accepted(item)
        ]
        return HealthReportResponse(insights=accepted)

    def _is_accepted(self, item: dict) -> bool:
        source = item.get("source")
        if not source:
            return False
        confidence = Decimal(str(item.get("confidence", "0")))
        return confidence >= MIN_CONFIDENCE
