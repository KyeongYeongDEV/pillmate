from __future__ import annotations

import asyncio
from decimal import Decimal

import pytest

from app.domain.ocr import OcrItem, PrescriptionOcrRequest, RawOcrItem
from app.rag.ocr.cache import InMemoryOcrResultCache
from app.rag.ocr.matcher import MatchResult
from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.service import OcrPrescriptionService

SAMPLE_URL = "https://s3.test/prescriptions/2026/06/uuid.jpg?sig=x"


class StubFetcher:
    def __init__(self, payload: bytes = b"SAME_IMAGE_BYTES"):
        self._payload = payload

    async def fetch(self, url: str) -> bytes:
        return self._payload


class GatedCountingVision:
    """extract() 호출 수를 세고, gate 가 풀릴 때까지 owner 를 in-flight 상태로 유지."""

    def __init__(self, raw_items: list[RawOcrItem], error: Exception | None = None):
        self._raw_items = raw_items
        self._error = error
        self.call_count = 0
        self.gate = asyncio.Event()

    async def extract(self, image_bytes: bytes) -> list[RawOcrItem]:
        self.call_count += 1
        await self.gate.wait()
        if self._error is not None:
            raise self._error
        return list(self._raw_items)


class StubMatcher:
    async def match(self, parsed: ParsedItem, raw: RawOcrItem) -> MatchResult:
        item = OcrItem(
            kd_code="KD001", name_raw=raw.name_raw,
            matched_name="타이레놀500mg", confidence=raw.confidence,
        )
        return MatchResult(item=item, stage="ilike")


def _request() -> PrescriptionOcrRequest:
    return PrescriptionOcrRequest(image_url=SAMPLE_URL, image_key="prescriptions/uuid.jpg")


def _service(vision, cache=None) -> OcrPrescriptionService:
    return OcrPrescriptionService(
        fetcher=StubFetcher(),
        vision=vision,
        matcher=StubMatcher(),
        cache=cache,
    )


def _raw():
    return [RawOcrItem(name_raw="타이레놀", confidence=Decimal("0.92"))]


@pytest.mark.asyncio
async def test_in_flight_dedupe_same_hash_single_llm_call():
    vision = GatedCountingVision(_raw())
    service = _service(vision)

    tasks = [asyncio.create_task(service.process(_request())) for _ in range(5)]
    await asyncio.sleep(0.05)  # 5 요청 모두 get_or_create 도달 (owner 1, joiner 4)
    vision.gate.set()
    results = await asyncio.gather(*tasks)

    assert vision.call_count == 1
    assert len(results) == 5
    assert all(r.items[0].matched_name == "타이레놀500mg" for r in results)


@pytest.mark.asyncio
async def test_in_flight_release_after_success():
    vision = GatedCountingVision(_raw())
    service = _service(vision)
    vision.gate.set()

    await service.process(_request())

    assert service._in_flight.in_flight_count() == 0


@pytest.mark.asyncio
async def test_in_flight_release_after_exception():
    vision = GatedCountingVision(_raw(), error=RuntimeError("vision down"))
    service = _service(vision)
    vision.gate.set()

    with pytest.raises(RuntimeError):
        await service.process(_request())

    assert service._in_flight.in_flight_count() == 0


@pytest.mark.asyncio
async def test_cache_hit_skips_in_flight():
    cache = InMemoryOcrResultCache()
    vision = GatedCountingVision(_raw())
    service = _service(vision, cache=cache)
    vision.gate.set()
    first = await service.process(_request())  # owner 처리 + 캐시 저장
    assert vision.call_count == 1

    second = await service.process(_request())  # 캐시 hit → vision 미호출, in-flight 미생성

    assert vision.call_count == 1
    assert second.items[0].matched_name == first.items[0].matched_name
    assert service._in_flight.in_flight_count() == 0


@pytest.mark.asyncio
async def test_owner_failure_propagates_to_joiners():
    vision = GatedCountingVision(_raw(), error=RuntimeError("owner failed"))
    service = _service(vision)

    tasks = [asyncio.create_task(service.process(_request())) for _ in range(5)]
    await asyncio.sleep(0.05)
    vision.gate.set()
    results = await asyncio.gather(*tasks, return_exceptions=True)

    assert vision.call_count == 1
    assert all(isinstance(r, RuntimeError) for r in results)
    assert service._in_flight.in_flight_count() == 0
