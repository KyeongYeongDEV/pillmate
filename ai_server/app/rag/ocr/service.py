from __future__ import annotations

import logging
from typing import Protocol

from app.domain.ocr import (
    OcrItem,
    PrescriptionOcrRequest,
    PrescriptionOcrResponse,
    RawOcrItem,
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
    ):
        self._fetcher = fetcher
        self._vision = vision
        self._matcher = matcher

    async def process(self, request: PrescriptionOcrRequest) -> PrescriptionOcrResponse:
        image_bytes = await self._fetcher.fetch(str(request.image_url))
        raw_items = await self._vision.extract(image_bytes)
        items = await self._match_all(raw_items)
        self._log_done(request, items)
        return PrescriptionOcrResponse(items=items)

    async def _match_all(self, raw_items: list[RawOcrItem]) -> list[OcrItem]:
        return [await self._matcher.match(raw) for raw in raw_items]

    def _log_done(self, request: PrescriptionOcrRequest, items: list[OcrItem]) -> None:
        kd_codes = [item.kd_code for item in items]
        logger.info(
            "OcrProcessed request_id=%s item_count=%d kd_codes=%s",
            request.request_id,
            len(items),
            kd_codes,
        )
