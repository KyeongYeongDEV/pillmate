from __future__ import annotations

from decimal import Decimal
from typing import Literal
from uuid import UUID

from typing import Any

from pydantic import BaseModel, ConfigDict, Field, HttpUrl

from app.domain.pill_appearance import PillAppearance

MFDS_SOURCE = "식품의약품안전처"
DEFAULT_FREQUENCY = 3
OCR_CONFIDENCE_FLOOR = Decimal("0.0")
OCR_CONFIDENCE_CEIL = Decimal("1.0")


class RawOcrItem(BaseModel):
    model_config = ConfigDict(extra="ignore")

    name_raw: str = Field(min_length=1)
    dose_amount: Decimal | None = None
    dose_unit: str | None = None
    frequency: int = DEFAULT_FREQUENCY
    duration_days: int | None = None
    confidence: Decimal = Field(ge=OCR_CONFIDENCE_FLOOR, le=OCR_CONFIDENCE_CEIL)
    candidates: list[str] = Field(default_factory=list)
    appearance: PillAppearance | None = None


class OcrItem(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    kd_code: str | None = None
    name_raw: str
    matched_name: str | None = None
    dose_amount: Decimal | None = None
    dose_unit: str | None = None
    frequency: int = DEFAULT_FREQUENCY
    duration_days: int | None = None
    confidence: Decimal = Field(ge=OCR_CONFIDENCE_FLOOR, le=OCR_CONFIDENCE_CEIL)


class OcrItemWithDecision(OcrItem):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    decision: Literal["AUTO", "CONFIRM", "MANUAL"] = "AUTO"
    decision_reason: str = "confident"
    candidate_options: list[dict[str, Any]] = Field(default_factory=list)


class MatchDecisionResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["AUTO", "CONFIRM", "MANUAL"]
    primary: OcrItem | None
    options: list[OcrItem] = Field(default_factory=list)
    reason: str


class PrescriptionOcrRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    image_url: HttpUrl
    request_id: UUID | None = None
    image_key: str | None = None


class PrescriptionOcrResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    items: list[OcrItemWithDecision]
    source: Literal["식품의약품안전처"] = MFDS_SOURCE
    # 주민등록번호 감지 시 true — 등록 차단 + 재촬영 유도용 (개인정보보호법 고유식별정보)
    pii_detected: bool = False


class RawOcrItemList(BaseModel):
    """PydanticOutputParser 가 강제하는 LLM 응답 컨테이너."""

    model_config = ConfigDict(extra="ignore")

    items: list[RawOcrItem] = Field(default_factory=list)
    has_resident_number: bool = False


class RawOcrItems(list):
    """list[RawOcrItem] + 이미지 레벨 has_resident_number 플래그.

    VisionAdapter.extract 의 반환 타입(list[RawOcrItem])을 깨지 않으면서
    컨테이너 레벨 PII 감지 플래그를 파이프라인으로 전달한다.
    """

    def __init__(self, items: list[RawOcrItem] = (), has_resident_number: bool = False):
        super().__init__(items)
        self.has_resident_number = has_resident_number
