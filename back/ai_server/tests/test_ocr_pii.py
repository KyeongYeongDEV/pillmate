from __future__ import annotations

from decimal import Decimal

from fastapi.testclient import TestClient
from langchain_core.output_parsers import PydanticOutputParser

from app.api import ocr as ocr_api
from app.domain.ocr import OcrItem, RawOcrItem, RawOcrItemList, RawOcrItems
from app.main import create_app
from app.rag.ocr.matcher import MatchResult
from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.service import OcrPrescriptionService

SAMPLE_URL = "https://s3.test/prescriptions/2026/07/uuid.jpg?sig=x"


class StubFetcher:
    async def fetch(self, url: str) -> bytes:
        return b"FAKE_BYTES"


class StubVision:
    """extract 반환값을 그대로 돌려줌 — RawOcrItems(플래그 有) 전파 확인용."""

    def __init__(self, items):
        self._items = items

    async def extract(self, image_bytes: bytes):
        return self._items


class StubMatcher:
    def __init__(self, table: dict[str, OcrItem]):
        self._table = table

    async def match(self, parsed: ParsedItem, raw: RawOcrItem) -> MatchResult:
        item = self._table[raw.name_raw]
        stage = "ilike" if item.kd_code else "none"
        return MatchResult(item=item, stage=stage)


def _service(vision, matcher) -> OcrPrescriptionService:
    return OcrPrescriptionService(fetcher=StubFetcher(), vision=vision, matcher=matcher)


def _client(vision, matcher) -> TestClient:
    app = create_app()
    app.dependency_overrides[ocr_api.get_ocr_service] = lambda: _service(vision, matcher)
    return TestClient(app)


def _matched(name: str) -> OcrItem:
    return OcrItem(kd_code="KD001", name_raw=name, matched_name=name + "500mg", confidence=Decimal("0.9"))


def test_raw_ocr_item_list_parses_has_resident_number():
    parser = PydanticOutputParser(pydantic_object=RawOcrItemList)

    parsed = parser.parse('{"items": [], "has_resident_number": true}')

    assert parsed.has_resident_number is True


def test_raw_ocr_item_list_defaults_has_resident_number_false():
    parser = PydanticOutputParser(pydantic_object=RawOcrItemList)

    parsed = parser.parse('{"items": []}')

    assert parsed.has_resident_number is False


def test_ocr_response_pii_detected_true_when_vision_flags():
    raw_items = RawOcrItems(
        [RawOcrItem(name_raw="타이레놀", confidence=Decimal("0.9"))],
        has_resident_number=True,
    )
    client = _client(StubVision(raw_items), StubMatcher({"타이레놀": _matched("타이레놀")}))

    response = client.post(
        "/api/v1/ocr/prescription", json={"image_url": SAMPLE_URL},
    )

    assert response.status_code == 200
    assert response.json()["pii_detected"] is True


def test_ocr_response_pii_detected_false_for_plain_list():
    raw_items = [RawOcrItem(name_raw="타이레놀", confidence=Decimal("0.9"))]
    client = _client(StubVision(raw_items), StubMatcher({"타이레놀": _matched("타이레놀")}))

    response = client.post(
        "/api/v1/ocr/prescription", json={"image_url": SAMPLE_URL},
    )

    assert response.status_code == 200
    assert response.json()["pii_detected"] is False


def test_raw_ocr_items_preserves_flag_and_behaves_as_list():
    raw = RawOcrItem(name_raw="타이레놀", confidence=Decimal("0.9"))

    items = RawOcrItems([raw], has_resident_number=True)

    assert list(items) == [raw]
    assert len(items) == 1
    assert items.has_resident_number is True
