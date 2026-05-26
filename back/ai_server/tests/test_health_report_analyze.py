from __future__ import annotations

import json
from decimal import Decimal

import pytest

from app.domain.health_report import (
    DrugInput,
    HealthReportRequest,
    PatternInput,
)
from app.rag.health_report.service import HealthReportService


class StubLlm:
    def __init__(self, response: str | Exception):
        self._response = response

    async def invoke(self, system: str, user: str) -> str:
        if isinstance(self._response, BaseException):
            raise self._response
        return self._response


def _request() -> HealthReportRequest:
    return HealthReportRequest(
        patientId=2,
        periodType="WEEKLY",
        score=85,
        adherence_rate=Decimal("92.00"),
        patterns=[PatternInput(type="EVENING_MISS", label="저녁 누락", missed_count=7, total=30)],
        drugs=[DrugInput(kd_code="200500823", name="오페나딘서방정", efficacy="알레르기성 비염")],
    )


@pytest.mark.asyncio
async def test_analyze_health_report_returns_insights_with_source():
    payload = {
        "insights": [
            {
                "type": "WARNING",
                "severity": "WARN",
                "title": "저녁약을 자주 빠뜨려요",
                "description": "최근 7일 중 3일 저녁약을 빠뜨리셨어요. 참고용입니다. 약사·의사와 상담하세요.",
                "source": "PillMate AI 분석",
                "confidence": 0.92,
            }
        ]
    }
    service = HealthReportService(llm=StubLlm(json.dumps(payload, ensure_ascii=False)))

    response = await service.analyze(_request())

    assert len(response.insights) == 1
    assert response.insights[0].source == "PillMate AI 분석"
    assert response.insights[0].type == "WARNING"


@pytest.mark.asyncio
async def test_analyze_clamps_low_confidence_to_empty():
    payload = {
        "insights": [
            {
                "type": "RECOMMENDATION",
                "severity": "INFO",
                "title": "저나트륨 식단",
                "description": "...",
                "source": "대한당뇨병학회",
                "confidence": 0.5,
            }
        ]
    }
    service = HealthReportService(llm=StubLlm(json.dumps(payload, ensure_ascii=False)))

    response = await service.analyze(_request())

    assert response.insights == []


@pytest.mark.asyncio
async def test_analyze_rejects_insight_without_source():
    payload = {
        "insights": [
            {
                "type": "TREND",
                "severity": "INFO",
                "title": "복약률 개선 추세",
                "description": "지난 주보다 5% 향상",
                "source": "",
                "confidence": 0.95,
            }
        ]
    }
    service = HealthReportService(llm=StubLlm(json.dumps(payload, ensure_ascii=False)))

    response = await service.analyze(_request())

    assert response.insights == []


@pytest.mark.asyncio
async def test_analyze_returns_empty_when_llm_fails():
    service = HealthReportService(llm=StubLlm(RuntimeError("gemini timeout")))

    response = await service.analyze(_request())

    assert response.insights == []
