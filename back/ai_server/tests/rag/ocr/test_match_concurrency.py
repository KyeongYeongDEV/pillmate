"""
T-AI-OCR-LATENCY-30S (후속) — _match_all 아이템별 매칭을 순차→동시 실행 TDD

실측 근거: correction(Tier 3) 이 미해결 알약당 순차 Gemini 호출 → 3건 × 15~37s 순차합산.
match_with_fallback 자체(임계·후보 결정 로직)는 불변, 아이템 간 실행만 asyncio.gather 로 동시화.
"""
from __future__ import annotations

import asyncio
import time
from decimal import Decimal

import pytest

from app.domain.ocr import PrescriptionOcrRequest, RawOcrItem
from app.rag.ocr.matcher import MatchResult
from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.service import OcrPrescriptionService

SAMPLE_URL = "https://s3.test/prescriptions/2026/07/uuid.jpg?sig=x"
_DELAY_SEC = 0.2


class StubFetcher:
    async def fetch(self, url: str) -> bytes:
        return b"FAKE_BYTES"


class SlowMatcher:
    """아이템마다 correction/Tier 3 급 지연을 흉내내는 느린 matcher — 병렬화 검증용."""

    def __init__(self):
        self.concurrent_calls = 0
        self.max_concurrent = 0

    async def match(self, parsed: ParsedItem, raw: RawOcrItem) -> MatchResult:
        self.concurrent_calls += 1
        self.max_concurrent = max(self.max_concurrent, self.concurrent_calls)
        await asyncio.sleep(_DELAY_SEC)
        self.concurrent_calls -= 1
        return MatchResult(item=None, stage="none")


def _request() -> PrescriptionOcrRequest:
    return PrescriptionOcrRequest(image_url=SAMPLE_URL, image_key="prescriptions/uuid.jpg")


def _raw_items(n: int) -> list[RawOcrItem]:
    return [RawOcrItem(name_raw=f"약{i}", confidence=Decimal("0.9")) for i in range(n)]


class StubVision:
    def __init__(self, items):
        self._items = items

    async def extract(self, image_bytes: bytes) -> list[RawOcrItem]:
        return list(self._items)


@pytest.mark.asyncio
async def test_match_all_runs_items_concurrently_not_sequentially():
    matcher = SlowMatcher()
    service = OcrPrescriptionService(
        fetcher=StubFetcher(), vision=StubVision(_raw_items(3)), matcher=matcher,
    )

    start = time.monotonic()
    response = await service.process(_request())
    elapsed = time.monotonic() - start

    assert len(response.items) == 3
    # 순차면 3 * 0.2s = 0.6s+, 동시면 ~0.2s. 여유를 두고 0.4s 미만이면 동시 실행으로 판단.
    assert elapsed < 0.4
    assert matcher.max_concurrent >= 2  # 최소 2건 이상 동시에 in-flight 였음을 직접 확인


@pytest.mark.asyncio
async def test_match_all_preserves_result_order():
    class OrderTaggingMatcher:
        async def match(self, parsed: ParsedItem, raw: RawOcrItem) -> MatchResult:
            # 뒤 아이템일수록 더 오래 걸리게 해 순서가 완료순이 아니라 입력순으로 보존되는지 검증
            delay = 0.05 * (3 - int(raw.name_raw[-1]))
            await asyncio.sleep(delay)
            return MatchResult(item=None, stage="none", final_score=float(raw.name_raw[-1]))

    items = _raw_items(3)  # 약0, 약1, 약2
    service = OcrPrescriptionService(
        fetcher=StubFetcher(), vision=StubVision(items), matcher=OrderTaggingMatcher(),
    )

    response = await service.process(_request())

    assert [item.name_raw for item in response.items] == ["약0", "약1", "약2"]
