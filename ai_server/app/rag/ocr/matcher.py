from __future__ import annotations

import logging
from dataclasses import dataclass
from decimal import Decimal
from typing import Literal, Protocol

from app.domain.ocr import OcrItem, RawOcrItem
from app.rag.ocr.normalizer import first_token, normalize_drug_name

logger = logging.getLogger(__name__)

VECTOR_MIN_SCORE = Decimal("0.6")

MatchStage = Literal["ilike", "token", "vector", "none"]


@dataclass(frozen=True)
class MatchCandidate:
    kd_code: str
    name: str
    score: Decimal


@dataclass(frozen=True)
class MatchResult:
    item: OcrItem
    stage: MatchStage


class IlikeDrugSearch(Protocol):
    async def search(self, name: str) -> MatchCandidate | None: ...


class VectorDrugSearch(Protocol):
    async def search(self, name: str) -> MatchCandidate | None: ...


class DrugMatcher:
    def __init__(self, ilike: IlikeDrugSearch, vector: VectorDrugSearch):
        self._ilike = ilike
        self._vector = vector

    async def match(self, raw: RawOcrItem) -> MatchResult:
        candidate, stage = await self._resolve_candidate(raw.name_raw)
        if candidate is None:
            return MatchResult(item=self._unmatched(raw), stage="none")
        return MatchResult(item=self._matched(raw, candidate), stage=stage)

    async def _resolve_candidate(
        self, name_raw: str
    ) -> tuple[MatchCandidate | None, MatchStage]:
        normalized = normalize_drug_name(name_raw)
        hit = await self._ilike_or_none(normalized)
        if hit is not None:
            return hit, "ilike"

        token = first_token(name_raw)
        if token and token != normalized:
            hit = await self._ilike_or_none(token)
            if hit is not None:
                return hit, "token"

        vector_hit = await self._vector.search(name_raw)
        if self._is_vector_acceptable(vector_hit):
            return vector_hit, "vector"
        return None, "none"

    async def _ilike_or_none(self, query: str) -> MatchCandidate | None:
        if not query:
            return None
        return await self._ilike.search(query)

    @staticmethod
    def _is_vector_acceptable(candidate: MatchCandidate | None) -> bool:
        return candidate is not None and candidate.score >= VECTOR_MIN_SCORE

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
