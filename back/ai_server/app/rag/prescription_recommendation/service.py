from __future__ import annotations

import json
import logging
from decimal import Decimal
from typing import Protocol

from app.domain.prescription_recommendation import (
    InsightDraft,
    PrescriptionRecommendationRequest,
    PrescriptionRecommendationResponse,
)

logger = logging.getLogger(__name__)

MIN_CONFIDENCE = Decimal("0.7")

SYSTEM_PROMPT = (
    "당신은 복약 안전 코치입니다. 새로 등록된 처방전(약 목록)을 보고 환자가 알아두면 좋은 인사이트를 만듭니다.\n"
    "규칙:\n"
    "1. 모든 인사이트에 출처(source) 를 명시합니다. 식약처 의약품 정보를 인용할 때는 '식약처'.\n"
    "   일반 복약 코칭은 'PillMate AI 분석'.\n"
    "2. 처방 변경, 약 종류 변경, 용량 변경 권유 금지 (의사 영역).\n"
    "3. 병용 주의·식이 주의·복용 시간 팁만 허용합니다. '복용하세요' 식 지시 대신 '영향을 줄 수 있어요' 톤.\n"
    "4. 환자 이름이나 식별정보는 사용하지 않습니다.\n"
    "5. 응답은 JSON 객체이며 키 'insights' 배열 안에 각 인사이트는 다음 필드를 갖습니다:\n"
    "   type(WARNING/RECOMMENDATION/TREND), severity(INFO/WARN/CRITICAL),\n"
    "   title(<=30자), description(<=200자), source, confidence(0.0~1.0).\n"
    "6. 확실하지 않으면 인사이트를 만들지 마세요. 근거 없는 정보는 금지입니다.\n"
    "7. description 마지막에 '참고용입니다. 약사·의사와 상담하세요.' 의미를 포함합니다.\n"
)


class LlmRunner(Protocol):
    async def invoke(self, system: str, user: str) -> str: ...


class PrescriptionRecommendationService:

    def __init__(self, llm: LlmRunner):
        self._llm = llm

    async def analyze(
        self, request: PrescriptionRecommendationRequest
    ) -> PrescriptionRecommendationResponse:
        user_prompt = self._build_user_prompt(request)
        try:
            raw = await self._llm.invoke(SYSTEM_PROMPT, user_prompt)
        except Exception as exc:
            logger.warning("recommendation analyze failed type=%s", type(exc).__name__)
            return PrescriptionRecommendationResponse(insights=[])
        return self._parse(raw)

    def _build_user_prompt(self, request: PrescriptionRecommendationRequest) -> str:
        drugs = json.dumps([d.model_dump(mode="json") for d in request.drugs], ensure_ascii=False)
        return (
            f"등록된 약 목록: {drugs}\n"
            "위 약 목록만으로 병용 주의/식이 주의/복용 팁 관점의 인사이트 1~3개를 만들어 JSON 으로 응답하세요."
        )

    def _parse(self, raw: str) -> PrescriptionRecommendationResponse:
        cleaned = raw.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
        try:
            data = json.loads(cleaned)
        except json.JSONDecodeError:
            logger.warning("recommendation JSON parse failed")
            return PrescriptionRecommendationResponse(insights=[])
        accepted = [
            InsightDraft(**item)
            for item in data.get("insights", [])
            if self._is_accepted(item)
        ]
        return PrescriptionRecommendationResponse(insights=accepted)

    def _is_accepted(self, item: dict) -> bool:
        source = item.get("source")
        if not source:
            return False
        confidence = Decimal(str(item.get("confidence", "0")))
        return confidence >= MIN_CONFIDENCE
