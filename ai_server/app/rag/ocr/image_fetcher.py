from __future__ import annotations

import logging

import httpx

from app.exceptions import ImageFetchError

logger = logging.getLogger(__name__)

IMAGE_FETCH_TIMEOUT_SEC = 15.0


class HttpxImageFetcher:
    def __init__(self, timeout: float = IMAGE_FETCH_TIMEOUT_SEC):
        self._timeout = timeout

    async def fetch(self, url: str) -> bytes:
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.get(url)
                response.raise_for_status()
                return response.content
        except httpx.HTTPError as exc:
            logger.warning("image fetch failed: %s", exc.__class__.__name__)
            raise ImageFetchError(f"image fetch failed: {exc.__class__.__name__}") from exc
