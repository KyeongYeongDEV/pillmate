from __future__ import annotations

from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class PatternInput(BaseModel):
    type: str
    label: str = ""
    missed_count: int = 0
    total: int = 0
    drug_code: str = ""


class DrugInput(BaseModel):
    kd_code: str = ""
    name: str = ""
    efficacy: str = ""


class HealthReportRequest(BaseModel):
    patient_id: int = Field(alias="patientId")
    period_type: Literal["WEEKLY", "MONTHLY"] = Field(alias="periodType")
    score: int
    adherence_rate: Decimal
    patterns: list[PatternInput] = []
    drugs: list[DrugInput] = []

    model_config = ConfigDict(populate_by_name=True)


class InsightItem(BaseModel):
    type: Literal["WARNING", "RECOMMENDATION", "TREND"]
    severity: Literal["INFO", "WARN", "CRITICAL"]
    title: str
    description: str
    source: str
    confidence: Decimal = Decimal("0.9")


class HealthReportResponse(BaseModel):
    insights: list[InsightItem]
