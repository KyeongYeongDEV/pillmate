from __future__ import annotations

import logging
from decimal import Decimal

from langchain_core.output_parsers import PydanticOutputParser
from pydantic import BaseModel, ConfigDict, Field

from app.domain.ocr import (
    DEFAULT_FREQUENCY,
    OCR_CONFIDENCE_CEIL,
    OCR_CONFIDENCE_FLOOR,
    RawOcrItem,
)
from app.domain.pill_appearance import PillAppearance
from app.exceptions import OcrParseError
from app.rag.ocr.vision import VISION_TIMEOUT_SEC, AsyncChatModel, GeminiVisionAdapter

logger = logging.getLogger(__name__)

LITE_MODEL = "gemini-2.5-flash-lite"
# flash-lite 가 confidence 를 누락/null 로 반환할 때의 안전 기본값 — auto 임계치(0.7) 미만이라 사용자 확인(MANUAL) 유도 (medical-safety)
LITE_NULL_CONFIDENCE = Decimal("0.5")


class RawOcrItemLite(BaseModel):
    """flash-lite 응답 스키마 — 모델이 모르는 필드를 null 로 정직 반환하는 것을 허용 (flash 의 RawOcrItem 은 무변경)."""

    model_config = ConfigDict(extra="ignore")

    name_raw: str = Field(min_length=1)
    dose_amount: Decimal | None = None
    dose_unit: str | None = None
    frequency: int | None = None
    duration_days: int | None = None
    confidence: Decimal | None = None
    candidates: list[str] = Field(default_factory=list)
    appearance: PillAppearance | None = None


class RawOcrItemListLite(BaseModel):
    model_config = ConfigDict(extra="ignore")

    items: list[RawOcrItemLite] = Field(default_factory=list)


class GeminiVisionLiteAdapter(GeminiVisionAdapter):
    """flash-lite 전용 — GeminiVisionAdapter 구조 그대로, parser/schema 만 lite(Optional) 로 교체."""

    def __init__(
        self,
        llm: AsyncChatModel | None = None,
        api_keys: list[str] | None = None,
        model: str = LITE_MODEL,
        prompt: str | None = None,
        timeout_sec: float = VISION_TIMEOUT_SEC,
        fewshot_enabled: bool = False,
        _llms: list[AsyncChatModel] | None = None,
    ):
        super().__init__(
            llm=llm, api_keys=api_keys, model=model, prompt=prompt,
            timeout_sec=timeout_sec, fewshot_enabled=fewshot_enabled, _llms=_llms,
        )
        self._parser = PydanticOutputParser(pydantic_object=RawOcrItemListLite)
        raw_prompt = prompt or self._resolve_prompt(fewshot_enabled)
        self._prompt = raw_prompt.replace(
            "{format_instructions}", self._parser.get_format_instructions()
        )

    def _parse(self, content: str) -> list[RawOcrItem]:
        try:
            parsed = self._parser.parse(content)
        except Exception as exc:
            logger.warning("ocr lite response parse failed: %s", exc.__class__.__name__)
            raise OcrParseError(str(exc)) from exc
        return [self._to_raw(item) for item in parsed.items]

    @staticmethod
    def _to_raw(lite: RawOcrItemLite) -> RawOcrItem:
        return RawOcrItem(
            name_raw=lite.name_raw,
            dose_amount=lite.dose_amount,
            dose_unit=lite.dose_unit,
            frequency=lite.frequency if lite.frequency is not None else DEFAULT_FREQUENCY,
            duration_days=lite.duration_days,
            confidence=_clamp_confidence(lite.confidence),
            candidates=lite.candidates,
            appearance=lite.appearance,
        )


def _clamp_confidence(confidence: Decimal | None) -> Decimal:
    if confidence is None:
        return LITE_NULL_CONFIDENCE
    if confidence < OCR_CONFIDENCE_FLOOR:
        return OCR_CONFIDENCE_FLOOR
    if confidence > OCR_CONFIDENCE_CEIL:
        return OCR_CONFIDENCE_CEIL
    return confidence
