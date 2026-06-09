from __future__ import annotations

import json
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
from app.rag.ocr.correction import OcrCorrectionAdapter
from app.rag.ocr.matcher import MatchResult, MatchStage
from app.rag.ocr.normalizer import normalize_for_cascade
from app.rag.ocr.parser import ParsedItem, parse_drug_item
from app.rag.ocr.preprocess import ImagePreprocessor

logger = logging.getLogger(__name__)
_stage_logger = logging.getLogger(__name__ + ".stage")


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
        correction: OcrCorrectionAdapter | None = None,
        preprocessor: ImagePreprocessor | None = None,
    ):
        self._fetcher = fetcher
        self._vision = vision
        self._matcher = matcher
        self._cache = cache or NullOcrResultCache()
        self._correction = correction
        self._preprocessor = preprocessor

    async def process(self, request: PrescriptionOcrRequest) -> PrescriptionOcrResponse:
        image_bytes = await self._fetcher.fetch(str(request.image_url))
        hash_hex = image_hash(image_bytes)
        cached = await self._cache.get(hash_hex)
        if cached is not None:
            self._log_done(request, cached.items, stages=None, cache_hit=True)
            return cached
        if self._preprocessor is not None:
            image_bytes = self._apply_preprocess(image_bytes)
        response, stages = await self._build_response(image_bytes)
        await self._cache.set(hash_hex, response)
        self._log_done(request, response.items, stages=stages, cache_hit=False)
        return response

    def _apply_preprocess(self, image_bytes: bytes) -> bytes:
        try:
            return self._preprocessor.preprocess(image_bytes)
        except Exception as exc:
            logger.warning("preprocess failed — using original: %s", exc.__class__.__name__)
            return image_bytes

    async def _build_response(
        self, image_bytes: bytes
    ) -> tuple[PrescriptionOcrResponse, list[MatchStage]]:
        raw_items = await self._vision.extract(image_bytes)
        results = await self._match_all(raw_items)
        items = [self._to_decision_item(result, raw) for result, raw in zip(results, raw_items)]
        stages = [result.stage for result in results]
        self._log_stage_decisions(raw_items, results)
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
        results = []
        for raw in raw_items:
            result = await self._match_with_fallback(raw)
            results.append(result)
        return results

    async def _match_with_fallback(self, raw: RawOcrItem) -> MatchResult:
        # Tier 0: preprocessed name (manufacturer strip + normalize)
        normalized = normalize_for_cascade(raw.name_raw)
        name_to_try = normalized if normalized != raw.name_raw else raw.name_raw
        parsed = parse_drug_item(name_to_try)
        result = await self._matcher.match(parsed, raw)
        if result.item is not None:
            return result

        # Tier 2: Vision candidates from LLM prompt
        for candidate_name in (raw.candidates or []):
            parsed_c = parse_drug_item(candidate_name)
            r = await self._matcher.match(parsed_c, raw)
            if r.item is not None:
                return r

        # Tier 3: OCR correction LLM
        if self._correction is not None:
            corrections = await self._correction.correct(raw.name_raw)
            for correction_name in corrections:
                parsed_c = parse_drug_item(correction_name)
                r = await self._matcher.match(parsed_c, raw)
                if r.item is not None:
                    return r

        return result

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

    def _log_stage_decisions(
        self,
        raw_items: list[RawOcrItem],
        results: list[MatchResult],
    ) -> None:
        for raw, result in zip(raw_items, results):
            decision = result.decision
            kd_code = None
            decision_type = "NONE"
            decision_reason = "no_match"
            if decision is not None:
                decision_type = decision.type.value
                decision_reason = decision.reason
                if decision.primary is not None:
                    kd_code = decision.primary.item_seq
            _stage_logger.info(
                json.dumps(
                    {
                        "event": "ocr_stage_decision",
                        "stage": result.stage,
                        "final_score": result.final_score,
                        "matched_kd_code": kd_code,
                        "decision_type": decision_type,
                        "decision_reason": decision_reason,
                    },
                    ensure_ascii=False,
                )
            )

    @staticmethod
    def _format_matched(
        items: list[OcrItem], stages: list[MatchStage] | None
    ) -> list[str]:
        if stages is None:
            return [item.name_raw for item in items]
        return [f"{item.name_raw}→{stage}" for item, stage in zip(items, stages)]
