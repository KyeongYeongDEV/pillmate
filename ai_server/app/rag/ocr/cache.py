from __future__ import annotations

import hashlib
import logging
from typing import Protocol

from app.domain.ocr import PrescriptionOcrResponse

logger = logging.getLogger(__name__)

IMAGE_HASH_TTL_SEC = 60 * 60 * 24  # 24h, cost-aware: 동일 이미지 재요청 시 Gemini Vision 재호출 회피
OCR_CACHE_KEY_PREFIX = "ocr:prescription:"


def image_hash(image_bytes: bytes) -> str:
    return hashlib.sha256(image_bytes).hexdigest()


def build_cache_key(hash_hex: str) -> str:
    return f"{OCR_CACHE_KEY_PREFIX}{hash_hex}"


class OcrResultCache(Protocol):
    async def get(self, image_hash_hex: str) -> PrescriptionOcrResponse | None: ...
    async def set(self, image_hash_hex: str, response: PrescriptionOcrResponse) -> None: ...


class NullOcrResultCache:
    async def get(self, image_hash_hex: str) -> PrescriptionOcrResponse | None:
        return None

    async def set(self, image_hash_hex: str, response: PrescriptionOcrResponse) -> None:
        return None


class InMemoryOcrResultCache:
    def __init__(self) -> None:
        self._store: dict[str, str] = {}

    async def get(self, image_hash_hex: str) -> PrescriptionOcrResponse | None:
        raw = self._store.get(build_cache_key(image_hash_hex))
        if raw is None:
            return None
        return PrescriptionOcrResponse.model_validate_json(raw)

    async def set(self, image_hash_hex: str, response: PrescriptionOcrResponse) -> None:
        self._store[build_cache_key(image_hash_hex)] = response.model_dump_json()


class RedisOcrResultCache:
    def __init__(self, redis_client, ttl_sec: int = IMAGE_HASH_TTL_SEC):
        self._redis = redis_client
        self._ttl = ttl_sec

    async def get(self, image_hash_hex: str) -> PrescriptionOcrResponse | None:
        raw = await self._redis.get(build_cache_key(image_hash_hex))
        if raw is None:
            return None
        return PrescriptionOcrResponse.model_validate_json(raw)

    async def set(self, image_hash_hex: str, response: PrescriptionOcrResponse) -> None:
        await self._redis.set(
            build_cache_key(image_hash_hex),
            response.model_dump_json(),
            ex=self._ttl,
        )
