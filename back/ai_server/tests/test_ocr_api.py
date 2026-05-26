from __future__ import annotations

import asyncio
from decimal import Decimal
from typing import Iterable

import httpx
import pytest
from fastapi.testclient import TestClient

from app.api import ocr as ocr_api
from app.domain.ocr import OcrItem, PrescriptionOcrRequest, RawOcrItem
from app.exceptions import ImageFetchError, OcrParseError, VisionInvocationError
from app.main import create_app
from app.rag.ocr.cache import InMemoryOcrResultCache, OcrResultCache
from app.rag.ocr.matcher import MatchResult
from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.service import OcrPrescriptionService


SAMPLE_URL = "https://s3.test/prescriptions/2026/05/uuid.jpg?sig=x"


class StubFetcher:
    def __init__(self, payload: bytes | Exception = b"FAKE_BYTES"):
        self._payload = payload

    async def fetch(self, url: str) -> bytes:
        if isinstance(self._payload, BaseException):
            raise self._payload
        return self._payload


class StubVision:
    def __init__(self, items: Iterable[RawOcrItem] | Exception):
        self._items = items

    async def extract(self, image_bytes: bytes) -> list[RawOcrItem]:
        if isinstance(self._items, BaseException):
            raise self._items
        return list(self._items)


class StubMatcher:
    def __init__(self, table: dict[str, OcrItem]):
        self._table = table

    async def match(self, parsed: ParsedItem, raw: RawOcrItem) -> MatchResult:
        item = self._table[raw.name_raw]
        stage = "ilike" if item.kd_code else "none"
        return MatchResult(item=item, stage=stage)


def _build_client(service: OcrPrescriptionService) -> TestClient:
    app = create_app()
    app.dependency_overrides[ocr_api.get_ocr_service] = lambda: service
    return TestClient(app)


def _service(fetcher=None, vision=None, matcher=None, cache: OcrResultCache | None = None) -> OcrPrescriptionService:
    return OcrPrescriptionService(
        fetcher=fetcher or StubFetcher(),
        vision=vision or StubVision([]),
        matcher=matcher or StubMatcher({}),
        cache=cache,
    )


def test_ocr_returns_items_with_source_when_vision_succeeds():
    raw_items = [
        RawOcrItem(name_raw="타이레놀", confidence=Decimal("0.92")),
        RawOcrItem(name_raw="알수없는약", confidence=Decimal("0.50")),
    ]
    matched = {
        "타이레놀": OcrItem(
            kd_code="KD001",
            name_raw="타이레놀",
            matched_name="타이레놀500mg",
            confidence=Decimal("0.92"),
        ),
        "알수없는약": OcrItem(
            kd_code=None,
            name_raw="알수없는약",
            matched_name=None,
            confidence=Decimal("0.50"),
        ),
    }
    service = _service(vision=StubVision(raw_items), matcher=StubMatcher(matched))
    client = _build_client(service)

    response = client.post("/api/v1/ocr/prescription", json={"image_url": SAMPLE_URL})

    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "식품의약품안전처"
    assert len(body["items"]) == 2
    assert body["items"][0]["kd_code"] == "KD001"
    assert body["items"][0]["matched_name"] == "타이레놀500mg"
    assert body["items"][1]["kd_code"] is None


def test_ocr_clamps_confidence_when_match_lower_than_llm():
    raw_items = [RawOcrItem(name_raw="타이레놀", confidence=Decimal("0.95"))]
    matched = {
        "타이레놀": OcrItem(
            kd_code="KD001",
            name_raw="타이레놀",
            matched_name="타이레놀500mg",
            confidence=Decimal("0.60"),
        )
    }
    service = _service(vision=StubVision(raw_items), matcher=StubMatcher(matched))
    client = _build_client(service)

    response = client.post("/api/v1/ocr/prescription", json={"image_url": SAMPLE_URL})

    assert response.status_code == 200
    assert Decimal(response.json()["items"][0]["confidence"]) == Decimal("0.60")


def test_ocr_returns_empty_items_when_vision_yields_nothing():
    service = _service(vision=StubVision([]))
    client = _build_client(service)

    response = client.post("/api/v1/ocr/prescription", json={"image_url": SAMPLE_URL})

    assert response.status_code == 200
    assert response.json()["items"] == []
    assert response.json()["source"] == "식품의약품안전처"


def test_ocr_rejects_invalid_url():
    service = _service()
    client = _build_client(service)

    response = client.post("/api/v1/ocr/prescription", json={"image_url": "not-a-url"})

    assert response.status_code == 422


def test_ocr_returns_502_when_image_fetch_fails():
    service = _service(fetcher=StubFetcher(ImageFetchError("connect refused")))
    client = _build_client(service)

    response = client.post("/api/v1/ocr/prescription", json={"image_url": SAMPLE_URL})

    assert response.status_code == 502
    assert response.json()["detail"]["code"] == "OCR_001"


def test_ocr_returns_504_when_vision_times_out():
    raw_items = StubVision(asyncio.TimeoutError())
    service = _service(vision=raw_items)
    client = _build_client(service)

    response = client.post("/api/v1/ocr/prescription", json={"image_url": SAMPLE_URL})

    assert response.status_code == 504
    assert response.json()["detail"]["code"] == "OCR_002"


def test_ocr_returns_504_when_vision_invocation_fails():
    service = _service(vision=StubVision(VisionInvocationError("gemini quota")))
    client = _build_client(service)

    response = client.post("/api/v1/ocr/prescription", json={"image_url": SAMPLE_URL})

    assert response.status_code == 504
    assert response.json()["detail"]["code"] == "OCR_002"


def test_ocr_skips_vision_when_image_hash_cached():
    raw_items = [RawOcrItem(name_raw="타이레놀", confidence=Decimal("0.92"))]
    matched = {
        "타이레놀": OcrItem(
            kd_code="KD001",
            name_raw="타이레놀",
            matched_name="타이레놀500mg",
            confidence=Decimal("0.92"),
        )
    }
    vision = StubVision(raw_items)
    cache = InMemoryOcrResultCache()
    service = _service(vision=vision, matcher=StubMatcher(matched), cache=cache)
    client = _build_client(service)

    first = client.post("/api/v1/ocr/prescription", json={"image_url": SAMPLE_URL})
    assert first.status_code == 200

    vision._items = VisionInvocationError("should not be called on cache hit")
    second = client.post("/api/v1/ocr/prescription", json={"image_url": SAMPLE_URL})

    assert second.status_code == 200
    assert second.json()["items"][0]["kd_code"] == "KD001"


def test_ocr_returns_500_when_parse_fails():
    service = _service(vision=StubVision(OcrParseError("invalid json")))
    client = _build_client(service)

    response = client.post("/api/v1/ocr/prescription", json={"image_url": SAMPLE_URL})

    assert response.status_code == 500
    assert response.json()["detail"]["code"] == "OCR_003"
