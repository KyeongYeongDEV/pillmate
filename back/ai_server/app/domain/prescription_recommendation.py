from __future__ import annotations

from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class DrugContext(BaseModel):
    code: str = ""
    name: str = ""
    dose_amount: Decimal | None = None
    dose_unit: str = ""
    frequency: int | None = None
    duration_days: int | None = None


class PrescriptionRecommendationRequest(BaseModel):
    prescription_id: int = Field(alias="prescriptionId")
    patient_id: int = Field(alias="patientId")
    drugs: list[DrugContext] = []

    model_config = ConfigDict(populate_by_name=True)


class InsightDraft(BaseModel):
    type: Literal["WARNING", "RECOMMENDATION", "TREND"]
    severity: Literal["INFO", "WARN", "CRITICAL"]
    title: str
    description: str
    source: str
    confidence: Decimal = Decimal("0.9")


class PrescriptionRecommendationResponse(BaseModel):
    insights: list[InsightDraft]
