from __future__ import annotations

import json
from decimal import Decimal

import pytest

from app.domain.prescription_recommendation import (
    DrugContext,
    PrescriptionRecommendationRequest,
)
from app.rag.prescription_recommendation.service import PrescriptionRecommendationService


class StubLlm:
    def __init__(self, response: str | Exception):
        self._response = response

    async def invoke(self, system: str, user: str) -> str:
        if isinstance(self._response, BaseException):
            raise self._response
        return self._response


def _request() -> PrescriptionRecommendationRequest:
    return PrescriptionRecommendationRequest(
        prescriptionId=42,
        patientId=7,
        drugs=[
            DrugContext(
                code="200500823",
                name="메트포르민정500밀리그램",
                dose_amount=Decimal("1.00"),
                dose_unit="정",
                frequency=2,
                duration_days=30,
            )
        ],
    )


@pytest.mark.asyncio
async def test_analyze_returns_insights_with_source():
    payload = {
        "insights": [
            {
                "type": "RECOMMENDATION",
                "severity": "INFO",
                "title": "비타민 B12 영향 가능",
                "description": "장기 복용 시 비타민 B12 흡수에 영향을 줄 수 있어요. 참고용입니다. 약사·의사와 상담하세요.",
                "source": "식약처",
                "confidence": 0.9,
            }
        ]
    }
    service = PrescriptionRecommendationService(llm=StubLlm(json.dumps(payload, ensure_ascii=False)))

    response = await service.analyze(_request())

    assert len(response.insights) == 1
    assert response.insights[0].source == "식약처"
    assert response.insights[0].type == "RECOMMENDATION"


@pytest.mark.asyncio
async def test_analyze_clamps_low_confidence_to_empty():
    payload = {
        "insights": [
            {
                "type": "RECOMMENDATION",
                "severity": "INFO",
                "title": "낮은 신뢰도",
                "description": "...",
                "source": "식약처",
                "confidence": 0.5,
            }
        ]
    }
    service = PrescriptionRecommendationService(llm=StubLlm(json.dumps(payload, ensure_ascii=False)))

    response = await service.analyze(_request())

    assert response.insights == []


@pytest.mark.asyncio
async def test_analyze_rejects_insight_without_source():
    payload = {
        "insights": [
            {
                "type": "WARNING",
                "severity": "WARN",
                "title": "출처 없음",
                "description": "근거 없는 정보",
                "source": "",
                "confidence": 0.95,
            }
        ]
    }
    service = PrescriptionRecommendationService(llm=StubLlm(json.dumps(payload, ensure_ascii=False)))

    response = await service.analyze(_request())

    assert response.insights == []


@pytest.mark.asyncio
async def test_analyze_returns_empty_when_llm_fails():
    service = PrescriptionRecommendationService(llm=StubLlm(RuntimeError("gemini timeout")))

    response = await service.analyze(_request())

    assert response.insights == []


@pytest.mark.asyncio
async def test_analyze_returns_empty_on_malformed_json():
    service = PrescriptionRecommendationService(llm=StubLlm("not-a-json"))

    response = await service.analyze(_request())

    assert response.insights == []
