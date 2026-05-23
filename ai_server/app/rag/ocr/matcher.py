from __future__ import annotations

import logging
from dataclasses import dataclass
from decimal import Decimal
from typing import Protocol

from app.domain.ocr import OcrItem, RawOcrItem

logger = logging.getLogger(__name__)

VECTOR_MIN_SCORE = Decimal("0.6")


@dataclass(frozen=True)
class MatchCandidate:
    kd_code: str
    name: str
    score: Decimal


class IlikeDrugSearch(Protocol):
    async def search(self, name: str) -> MatchCandidate | None: ...


class VectorDrugSearch(Protocol):
    async def search(self, name: str) -> MatchCandidate | None: ...


class DrugMatcher:
    def __init__(self, ilike: IlikeDrugSearch, vector: VectorDrugSearch):
        self._ilike = ilike
        self._vector = vector

    async def match(self, raw: RawOcrItem) -> OcrItem:
        candidate = await self._resolve_candidate(raw.name_raw)
        if candidate is None:
            return self._unmatched(raw)
        return self._matched(raw, candidate)

    async def _resolve_candidate(self, name: str) -> MatchCandidate | None:
        ilike_hit = await self._ilike.search(name)
        if ilike_hit is not None:
            return ilike_hit
        vector_hit = await self._vector.search(name)
        if vector_hit is None or vector_hit.score < VECTOR_MIN_SCORE:
            return None
        return vector_hit

    def _matched(self, raw: RawOcrItem, candidate: MatchCandidate) -> OcrItem:
        confidence = min(raw.confidence, candidate.score)
        return OcrItem(
            kd_code=candidate.kd_code,
            name_raw=raw.name_raw,
            matched_name=candidate.name,
            dose_amount=raw.dose_amount,
            dose_unit=raw.dose_unit,
            frequency=raw.frequency,
            duration_days=raw.duration_days,
            confidence=confidence,
        )

    def _unmatched(self, raw: RawOcrItem) -> OcrItem:
        return OcrItem(
            kd_code=None,
            name_raw=raw.name_raw,
            matched_name=None,
            dose_amount=raw.dose_amount,
            dose_unit=raw.dose_unit,
            frequency=raw.frequency,
            duration_days=raw.duration_days,
            confidence=raw.confidence,
        )
