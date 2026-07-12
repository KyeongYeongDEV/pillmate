from __future__ import annotations

import asyncio
import logging

from app.domain.ocr import RawOcrItem
from app.exceptions import VisionBusyError, VisionInvocationError
from app.rag.ocr.vision import GeminiVisionAdapter
from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter

logger = logging.getLogger(__name__)

LOW_CONFIDENCE_THRESHOLD = 0.3
UNKNOWN_NAME_SENTINEL = "???"
# primary(lite) 를 빠르게 cutoff — lite+flash 합산이 BE read timeout(120s) 내 들도록 + lite quota 절약
CASCADE_PRIMARY_TIMEOUT_SEC = 15.0


class CascadeVisionAdapter:
    """lite 1차 → 실패/저품질 시 flash fallback (Model Cascade, cost-aware)."""

    def __init__(self, primary: GeminiVisionLiteAdapter, fallback: GeminiVisionAdapter):
        self._primary = primary
        self._fallback = fallback

    async def extract(self, image_bytes: bytes) -> list[RawOcrItem]:
        try:
            result = await self._primary.extract(image_bytes)
        except (VisionInvocationError, VisionBusyError, asyncio.TimeoutError) as exc:
            logger.warning("cascade primary failed type=%s → flash fallback", type(exc).__name__)
            return await self._fallback.extract(image_bytes)

        if self._is_low_quality(result):
            logger.info("cascade primary low_quality → flash fallback (items=%d)", len(result))
            return await self._fallback.extract(image_bytes)
        return result

    def _is_low_quality(self, items: list[RawOcrItem]) -> bool:
        if not items:
            return True
        if all(_unknown_name(item) for item in items):
            return True
        if all(_low_confidence(item) for item in items):
            return True
        return False


def _unknown_name(item: RawOcrItem) -> bool:
    name = getattr(item, "name_raw", None) or ""
    return name.strip() == UNKNOWN_NAME_SENTINEL


def _low_confidence(item: RawOcrItem) -> bool:
    conf = getattr(item, "confidence", None)
    return conf is None or float(conf) < LOW_CONFIDENCE_THRESHOLD
