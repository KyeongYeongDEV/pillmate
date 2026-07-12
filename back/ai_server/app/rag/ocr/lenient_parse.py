from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from decimal import Decimal

from pydantic import BaseModel, ValidationError

UNKNOWN_NAME_SENTINEL = "???"
# auto 임계치(0.70) 미만 안전값 — 손상된 confidence 를 MANUAL 확인으로 유도 (medical-safety 불변)
FALLBACK_CONFIDENCE_ON_CORRUPT = Decimal("0.5")

_JSON_FENCE_RE = re.compile(r"```(?:json)?\s*(.*?)\s*```", re.DOTALL)
_OPTIONAL_NUMERIC_FIELDS = ("dose_amount", "duration_days")


@dataclass
class LenientParseResult:
    items: list[BaseModel] = field(default_factory=list)
    has_resident_number: bool = False
    dropped_count: int = 0


def parse_items_leniently(content: str, item_model: type[BaseModel]) -> LenientParseResult:
    """RawOcrItemList 전체 파싱 실패 시 아이템별 부분 복구.

    깨진 아이템(이름 없음/"???"/필드 타입오염)만 드랍하고, 생존 아이템은 그대로 진행한다.
    이름 추측 보정은 하지 않는다 — 드랍된 건 어차피 사용자 확인(MANUAL) 단계가 있다.
    """
    payload = _extract_json(content)
    raw_items = payload.get("items") or []
    survivors: list[BaseModel] = []
    dropped = 0
    for raw in raw_items:
        item = _repair_and_validate(raw, item_model)
        if item is None:
            dropped += 1
        else:
            survivors.append(item)
    has_resident_number = bool(payload.get("has_resident_number", False))
    return LenientParseResult(items=survivors, has_resident_number=has_resident_number, dropped_count=dropped)


def _extract_json(content: str) -> dict:
    match = _JSON_FENCE_RE.search(content)
    text = match.group(1) if match else content
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        start, end = text.find("{"), text.rfind("}")
        if start == -1 or end == -1 or end <= start:
            raise
        return json.loads(text[start : end + 1])


def _repair_and_validate(raw: object, item_model: type[BaseModel]) -> BaseModel | None:
    if not isinstance(raw, dict):
        return None
    name = str(raw.get("name_raw") or "").strip()
    if not name or name == UNKNOWN_NAME_SENTINEL:
        return None
    repaired = _repair_numeric_fields(raw)
    try:
        return item_model.model_validate(repaired)
    except ValidationError:
        return None


def _repair_numeric_fields(raw: dict) -> dict:
    repaired = dict(raw)
    for field_name in _OPTIONAL_NUMERIC_FIELDS:
        value = repaired.get(field_name)
        if value is not None and not _is_numeric(value):
            repaired[field_name] = None
    frequency = repaired.get("frequency")
    if frequency is not None and not _is_numeric(frequency):
        repaired.pop("frequency", None)
    confidence = repaired.get("confidence")
    if not _is_numeric(confidence):
        repaired["confidence"] = str(FALLBACK_CONFIDENCE_ON_CORRUPT)
    return repaired


def _is_numeric(value: object) -> bool:
    if isinstance(value, (int, float)):
        return True
    try:
        float(str(value))
        return True
    except (TypeError, ValueError):
        return False
