from __future__ import annotations

from fastapi import APIRouter, Depends

from app.domain.health_report import HealthReportRequest, HealthReportResponse
from app.rag.health_report.service import HealthReportService

router = APIRouter(prefix="/api/v1")


def get_health_report_service() -> HealthReportService:
    raise RuntimeError("HealthReportService dependency must be overridden at app startup")


@router.post("/analyze/health-report", response_model=HealthReportResponse)
async def analyze_health_report(
    request: HealthReportRequest,
    service: HealthReportService = Depends(get_health_report_service),
) -> HealthReportResponse:
    return await service.analyze(request)
