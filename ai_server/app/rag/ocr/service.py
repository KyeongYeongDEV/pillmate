from __future__ import annotations

import logging
from typing import Protocol

from app.domain.ocr import (
    OcrItem,
    OcrItemWithDecision,
    PrescriptionOcrRequest,
    PrescriptionOcrResponse,
    RawOcrItem,
)
from app.rag.ocr.cache import (
    NullOcrResultCache,
    OcrResultCache,
    image_hash,
)
from app.rag.ocr.matcher import MatchResult, MatchStage
from app.rag.ocr.parser import ParsedItem, parse_drug_item

logger = logging.getLogger(__name__)


class ImageFetcher(Protocol):
    async def fetch(self, url: str) -> bytes: ...


class VisionAdapter(Protocol):
    async def extract(self, image_bytes: bytes) -> list[RawOcrItem]: ...


class DrugMatcherPort(Protocol):
    async def match(self, parsed: ParsedItem, raw: RawOcrItem) -> MatchResult: ...


class OcrPrescriptionService:
    def __init__(
        self,
        fetcher: ImageFetcher,
        vision: VisionAdapter,
        matcher: DrugMatcherPort,
        cache: OcrResultCache | None = None,
    ):
        self._fetcher = fetcher
        self._vision = vision
        self._matcher = matcher
        self._cache = cache or NullOcrResultCache()

    async def process(self, request: PrescriptionOcrRequest) -> PrescriptionOcrResponse:
        image_bytes = await self._fetcher.fetch(str(request.image_url))
        hash_hex = image_hash(image_bytes)
        cached = await self._cache.get(hash_hex)
        if cached is not None:
            self._log_done(request, cached.items, stages=None, cache_hit=True)
            return cached
        response, stages = await self._build_response(image_bytes)
        await self._cache.set(hash_hex, response)
        self._log_done(request, response.items, stages=stages, cache_hit=False)
        return response

    async def _build_response(
        self, image_bytes: bytes
    ) -> tuple[PrescriptionOcrResponse, list[MatchStage]]:
        raw_items = await self._vision.extract(image_bytes)
        results = await self._match_all(raw_items)
        items = [self._to_decision_item(result, raw) for result, raw in zip(results, raw_items)]
        stages = [result.stage for result in results]
        return PrescriptionOcrResponse(items=items), stages

    def _to_decision_item(self, result: MatchResult, raw: RawOcrItem) -> OcrItemWithDecision:
        if result.item is not None:
            return OcrItemWithDecision(**result.item.model_dump())
        decision = result.decision
        if decision is None or decision.primary is None:
            return OcrItemWithDecision(
                kd_code=None,
                name_raw=raw.name_raw,
                confidence=raw.confidence,
                decision="MANUAL",
                decision_reason="no_match",
            )
        primary = decision.primary
        return OcrItemWithDecision(
            kd_code=primary.item_seq,
            name_raw=raw.name_raw,
            matched_name=primary.name,
            dose_amount=primary.dose_amount,
            dose_unit=primary.dose_unit,
            confidence=raw.confidence,
            decision=decision.type.value,
            decision_reason=decision.reason,
            candidate_options=[
                {"item_seq": c.item_seq, "name": c.name,
                 "dose_amount": str(c.dose_amount) if c.dose_amount else None,
                 "dose_unit": c.dose_unit}
                for c in (decision.options or [])
            ],
        )

    async def _match_all(self, raw_items: list[RawOcrItem]) -> list[MatchResult]:
        parsed_items = [parse_drug_item(raw.name_raw) for raw in raw_items]
        return [
            await self._matcher.match(parsed, raw)
            for parsed, raw in zip(parsed_items, raw_items)
        ]

    def _log_done(
        self,
        request: PrescriptionOcrRequest,
        items: list[OcrItem],
        stages: list[MatchStage] | None,
        cache_hit: bool,
    ) -> None:
        logger.info(
            "OcrProcessed request_id=%s item_count=%d matched=%s cache_hit=%s",
            request.request_id,
            len(items),
            self._format_matched(items, stages),
            cache_hit,
        )

    @staticmethod
    def _format_matched(
        items: list[OcrItem], stages: list[MatchStage] | None
    ) -> list[str]:
        if stages is None:
            return [item.name_raw for item in items]
        return [f"{item.name_raw}→{stage}" for item, stage in zip(items, stages)]
