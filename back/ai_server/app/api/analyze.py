from __future__ import annotations

from fastapi import APIRouter, Depends

from app.domain.health_report import HealthReportRequest, HealthReportResponse
from app.domain.prescription_recommendation import (
    PrescriptionRecommendationRequest,
    PrescriptionRecommendationResponse,
)
from app.rag.health_report.service import HealthReportService
from app.rag.prescription_recommendation.service import PrescriptionRecommendationService

router = APIRouter(prefix="/api/v1")


def get_health_report_service() -> HealthReportService:
    raise RuntimeError("HealthReportService dependency must be overridden at app startup")


def get_prescription_recommendation_service() -> PrescriptionRecommendationService:
    raise RuntimeError(
        "PrescriptionRecommendationService dependency must be overridden at app startup"
    )


@router.post("/analyze/health-report", response_model=HealthReportResponse)
async def analyze_health_report(
    request: HealthReportRequest,
    service: HealthReportService = Depends(get_health_report_service),
) -> HealthReportResponse:
    return await service.analyze(request)


@router.post(
    "/analyze/prescription-recommendation",
    response_model=PrescriptionRecommendationResponse,
)
async def analyze_prescription_recommendation(
    request: PrescriptionRecommendationRequest,
    service: PrescriptionRecommendationService = Depends(
        get_prescription_recommendation_service
    ),
) -> PrescriptionRecommendationResponse:
    return await service.analyze(request)
