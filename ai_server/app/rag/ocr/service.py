from __future__ import annotations

import logging
from typing import Protocol

from app.domain.ocr import (
    OcrItem,
    PrescriptionOcrRequest,
    PrescriptionOcrResponse,
    RawOcrItem,
)
from app.rag.ocr.cache import (
    NullOcrResultCache,
    OcrResultCache,
    image_hash,
)

logger = logging.getLogger(__name__)


class ImageFetcher(Protocol):
    async def fetch(self, url: str) -> bytes: ...


class VisionAdapter(Protocol):
    async def extract(self, image_bytes: bytes) -> list[RawOcrItem]: ...


class DrugMatcherPort(Protocol):
    async def match(self, raw: RawOcrItem) -> OcrItem: ...


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
            self._log_done(request, cached.items, cache_hit=True)
            return cached
        response = await self._build_response(image_bytes)
        await self._cache.set(hash_hex, response)
        self._log_done(request, response.items, cache_hit=False)
        return response

    async def _build_response(self, image_bytes: bytes) -> PrescriptionOcrResponse:
        raw_items = await self._vision.extract(image_bytes)
        items = await self._match_all(raw_items)
        return PrescriptionOcrResponse(items=items)

    async def _match_all(self, raw_items: list[RawOcrItem]) -> list[OcrItem]:
        return [await self._matcher.match(raw) for raw in raw_items]

    def _log_done(
        self, request: PrescriptionOcrRequest, items: list[OcrItem], cache_hit: bool
    ) -> None:
        logger.info(
            "OcrProcessed request_id=%s item_count=%d kd_codes=%s cache_hit=%s",
            request.request_id,
            len(items),
            [item.kd_code for item in items],
            cache_hit,
        )
