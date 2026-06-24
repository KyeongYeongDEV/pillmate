from __future__ import annotations

import asyncio
import json
import logging
import time
from typing import TYPE_CHECKING, Protocol

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
from app.rag.ocr.match_logger import OcrMatchLogEntry, OcrMatchLogger
from app.rag.ocr.matcher import MatchCandidate, MatchResult, MatchStage
from app.rag.ocr.pill_identify import PillIdentifyAdapter
from app.rag.ocr.normalizer import normalize_for_cascade
from app.rag.ocr.parser import ParsedItem, parse_drug_item
from app.rag.ocr.rrf import Candidate, MatchDecision, MatchDecisionType

if TYPE_CHECKING:
    from app.rag.ocr.preprocess import ImagePreprocessor

logger = logging.getLogger(__name__)
_stage_logger = logging.getLogger(__name__ + ".stage")

_SURFACED_RANK_ATTRS = ("exact_rank", "trgm_rank", "jamo_rank", "vector_rank")
_SURFACED_NAMES = ("exact", "trigram", "jamo", "vector")
_LOG_CANDIDATES_TOP_N = 5


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
        pill_identifier: PillIdentifyAdapter | None = None,
        match_logger: OcrMatchLogger | None = None,
    ):
        self._fetcher = fetcher
        self._vision = vision
        self._matcher = matcher
        self._cache = cache or NullOcrResultCache()
        self._correction = correction
        self._preprocessor = preprocessor
        self._pill_identifier = pill_identifier
        self._match_logger = match_logger

    async def process(self, request: PrescriptionOcrRequest) -> PrescriptionOcrResponse:
        image_bytes = await self._fetcher.fetch(str(request.image_url))
        hash_hex = image_hash(image_bytes)
        cached = await self._cache.get(hash_hex)
        if cached is not None:
            self._log_done(request, cached.items, stages=None, cache_hit=True)
            return cached
        if self._preprocessor is not None:
            image_bytes = self._apply_preprocess(image_bytes)
        response, stages = await self._build_response(
            image_bytes, hash_hex=hash_hex, image_key=request.image_key
        )
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
        self,
        image_bytes: bytes,
        hash_hex: str | None = None,
        image_key: str | None = None,
    ) -> tuple[PrescriptionOcrResponse, list[MatchStage]]:
        raw_items = await self._vision.extract(image_bytes)
        results = await self._match_all(raw_items, hash_hex=hash_hex, image_key=image_key)
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

    async def _match_all(
        self,
        raw_items: list[RawOcrItem],
        hash_hex: str | None = None,
        image_key: str | None = None,
    ) -> list[MatchResult]:
        results = []
        for raw in raw_items:
            t0 = int(time.monotonic() * 1000)
            result = await self._match_with_fallback(raw)
            latency_ms = int(time.monotonic() * 1000) - t0
            results.append(result)
            if self._match_logger is not None:
                entry = self._build_log_entry(raw, result, hash_hex, image_key, latency_ms)
                asyncio.ensure_future(self._safe_log(entry))
        return results

    def _build_log_entry(
        self,
        raw: RawOcrItem,
        result: MatchResult,
        hash_hex: str | None,
        image_key: str | None,
        latency_ms: int,
    ) -> OcrMatchLogEntry:
        decision = result.decision
        primary = decision.primary if decision is not None else None

        matched_kd_code = primary.item_seq if primary is not None else None
        matched_drug_name = primary.name if primary is not None else None
        rrf_score = primary.rrf_score if primary is not None else None
        reranker_score = primary.final_score if primary is not None else None
        decision_str = decision.type.value if decision is not None else None

        surfaced_by = self._surfaced_by(primary) if primary is not None else None
        candidates_json = self._candidates_json(decision)
        gemini_raw_json = raw.model_dump_json()

        return OcrMatchLogEntry(
            image_hash=hash_hex,
            image_key=image_key,
            raw_ocr_text=raw.name_raw,
            matched_kd_code=matched_kd_code,
            matched_drug_name=matched_drug_name,
            decision=decision_str,
            final_score=result.final_score,
            rrf_score=rrf_score,
            reranker_score=reranker_score,
            surfaced_by=surfaced_by,
            candidates_json=candidates_json,
            gemini_raw_json=gemini_raw_json,
            latency_ms=latency_ms,
        )

    @staticmethod
    def _surfaced_by(primary: Candidate) -> str | None:
        names = [
            label
            for attr, label in zip(_SURFACED_RANK_ATTRS, _SURFACED_NAMES)
            if getattr(primary, attr, None) is not None
        ]
        return ",".join(names) if names else None

    @staticmethod
    def _candidates_json(decision: MatchDecision | None) -> str | None:
        if decision is None or not decision.options:
            return None
        top = decision.options[:_LOG_CANDIDATES_TOP_N]
        payload = [
            {"kdCode": c.item_seq, "name": c.name, "score": c.final_score, "rank": idx + 1}
            for idx, c in enumerate(top)
        ]
        return json.dumps(payload, ensure_ascii=False)

    async def _safe_log(self, entry: OcrMatchLogEntry) -> None:
        try:
            await self._match_logger.insert(entry)
        except Exception as exc:
            logger.warning("match log fire failed: %s", exc)

    async def _match_with_fallback(self, raw: RawOcrItem) -> MatchResult:
        # Tier 0: preprocessed name (manufacturer strip + normalize)
        normalized = normalize_for_cascade(raw.name_raw)
        name_to_try = normalized if normalized != raw.name_raw else raw.name_raw
        parsed = parse_drug_item(name_to_try)
        result = await self._matcher.match(parsed, raw)
        if self._is_definitive(result):
            return result

        # Tier 2: Vision candidates from LLM prompt
        for candidate_name in (raw.candidates or []):
            parsed_c = parse_drug_item(candidate_name)
            r = await self._matcher.match(parsed_c, raw)
            if self._is_definitive(r):
                return r

        # Tier 3: OCR correction LLM
        if self._correction is not None:
            corrections = await self._correction.correct(raw.name_raw)
            for correction_name in corrections:
                parsed_c = parse_drug_item(correction_name)
                r = await self._matcher.match(parsed_c, raw)
                if self._is_definitive(r):
                    return r

        # Tier 5: 낱알식별 fallback (shape/color/mark → DB)
        if self._pill_identifier is not None and raw.appearance is not None:
            pill_candidates = await self._pill_identifier.identify(raw.appearance)
            if pill_candidates:
                best = pill_candidates[0]
                return self._pill_candidate_to_result(best, raw)

        return result

    @staticmethod
    def _is_definitive(result: MatchResult) -> bool:
        """DrugMatcher(item!=None) 또는 RrfMatcher(AUTO/CONFIRM decision) 에서 확정 매치."""
        if result.item is not None:
            return True
        d = result.decision
        if d is None:
            return False
        return d.type in (MatchDecisionType.AUTO, MatchDecisionType.CONFIRM)

    def _pill_candidate_to_result(
        self, candidate: MatchCandidate, raw: RawOcrItem
    ) -> MatchResult:
        item = OcrItem(
            kd_code=candidate.kd_code,
            name_raw=raw.name_raw,
            matched_name=candidate.name,
            dose_amount=raw.dose_amount,
            dose_unit=raw.dose_unit,
            frequency=raw.frequency,
            duration_days=raw.duration_days,
            confidence=raw.confidence,
        )
        return MatchResult(item=item, stage="pill_identify", final_score=float(candidate.score))

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
