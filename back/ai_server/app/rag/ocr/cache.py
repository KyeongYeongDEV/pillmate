from __future__ import annotations

import asyncio
import hashlib
import logging
from typing import Protocol

from app.domain.ocr import PrescriptionOcrResponse

logger = logging.getLogger(__name__)

IMAGE_HASH_TTL_SEC = 300  # 5분 (cost-aware.md 규정 준수) — 영구 TTL 금지, 동일 이미지 재요청 sub-second 반환
OCR_CACHE_KEY_PREFIX = "ocr:v1:"  # v1 — 파이프라인 변경 시 bump 하여 stale 캐시 무효화


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


class InFlightRegistry:
    """동일 image hash 의 동시 OCR 요청을 1회 처리로 합치는 in-process dedupe (cost-aware: Gemini 중복 호출 회피)."""

    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self._futures: dict[str, asyncio.Future] = {}

    async def get_or_create(self, key: str) -> tuple[asyncio.Future, bool]:
        async with self._lock:
            existing = self._futures.get(key)
            if existing is not None:
                return existing, False
            future = asyncio.get_running_loop().create_future()
            self._futures[key] = future
            return future, True

    async def complete(self, key: str, result: PrescriptionOcrResponse) -> None:
        future = await self._pop(key)
        if future is not None and not future.done():
            future.set_result(result)

    async def fail(self, key: str, exc: BaseException) -> None:
        future = await self._pop(key)
        if future is not None and not future.done():
            future.set_exception(exc)

    def in_flight_count(self) -> int:
        return len(self._futures)

    async def _pop(self, key: str) -> asyncio.Future | None:
        async with self._lock:
            return self._futures.pop(key, None)
