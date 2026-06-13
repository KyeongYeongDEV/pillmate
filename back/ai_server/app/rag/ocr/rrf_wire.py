"""
RrfMatcherAdapter — DrugMatcherPort 시그니처 호환 래퍼.

OcrPrescriptionService.match(parsed, raw) → RrfMatcher.match(parsed)
"""
from __future__ import annotations

from dataclasses import replace

from app.domain.ocr import RawOcrItem
from app.rag.ocr.matcher import MatchResult
from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.rrf_matcher import RrfMatcher


class RrfMatcherAdapter:
    """RrfMatcher 를 DrugMatcherPort 시그니처에 맞게 래핑한다."""

    def __init__(self, rrf_matcher: RrfMatcher) -> None:
        self._rrf = rrf_matcher

    async def match(self, parsed: ParsedItem, raw: RawOcrItem) -> MatchResult:
        # normalize_for_cascade 가 단위 제거 시 dose_amount 가 손실되는 경우 보충
        if parsed.dose_amount is None and raw.dose_amount is not None:
            parsed = replace(
                parsed,
                dose_amount=raw.dose_amount,
                dose_unit=raw.dose_unit or "mg",
            )
        return await self._rrf.match(parsed)
