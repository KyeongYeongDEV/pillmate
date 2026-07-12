"""
T-OCR-CASCADE — CascadeVisionAdapter (lite 1차 → flash fallback) TDD

primary(lite) 정상/저품질/예외에 따라 fallback(flash) escalate 여부 검증.
"""
from __future__ import annotations

import asyncio
from decimal import Decimal

import pytest
from unittest.mock import AsyncMock

from app.domain.ocr import RawOcrItem
from app.exceptions import VisionInvocationError
from app.rag.ocr.cascade_vision import CascadeVisionAdapter


def _item(name: str = "타이레놀정", confidence: str = "0.95") -> RawOcrItem:
    return RawOcrItem(name_raw=name, confidence=Decimal(confidence))


def _cascade(primary_result=None, primary_exc=None, fallback_result=None, fallback_exc=None):
    primary = AsyncMock()
    if primary_exc is not None:
        primary.extract.side_effect = primary_exc
    else:
        primary.extract.return_value = primary_result
    fallback = AsyncMock()
    if fallback_exc is not None:
        fallback.extract.side_effect = fallback_exc
    else:
        fallback.extract.return_value = fallback_result
    return CascadeVisionAdapter(primary, fallback), primary, fallback


@pytest.mark.asyncio
async def test_cascade_primary_success_returns_primary():
    cascade, primary, fallback = _cascade(primary_result=[_item("게보린정", "0.9")])
    result = await cascade.extract(b"img")
    assert result[0].name_raw == "게보린정"
    primary.extract.assert_awaited_once()
    fallback.extract.assert_not_awaited()


@pytest.mark.asyncio
async def test_cascade_primary_exception_fallback():
    cascade, primary, fallback = _cascade(
        primary_exc=VisionInvocationError("lite quota"),
        fallback_result=[_item("아목시실린", "0.92")])
    result = await cascade.extract(b"img")
    assert result[0].name_raw == "아목시실린"
    primary.extract.assert_awaited_once()
    fallback.extract.assert_awaited_once()


@pytest.mark.asyncio
async def test_cascade_primary_timeout_fallback():
    cascade, primary, fallback = _cascade(
        primary_exc=asyncio.TimeoutError(),
        fallback_result=[_item("게보린정", "0.9")])
    result = await cascade.extract(b"img")
    assert result[0].name_raw == "게보린정"
    fallback.extract.assert_awaited_once()


@pytest.mark.asyncio
async def test_cascade_primary_low_quality_unknown_name_fallback():
    cascade, primary, fallback = _cascade(
        primary_result=[_item("???", "0.9"), _item("???", "0.8")],
        fallback_result=[_item("타이레놀정", "0.95")])
    result = await cascade.extract(b"img")
    assert result[0].name_raw == "타이레놀정"
    fallback.extract.assert_awaited_once()


@pytest.mark.asyncio
async def test_cascade_primary_low_confidence_fallback():
    cascade, primary, fallback = _cascade(
        primary_result=[_item("타이레놀정", "0.1"), _item("게보린정", "0.2")],
        fallback_result=[_item("타이레놀정", "0.95")])
    result = await cascade.extract(b"img")
    assert result[0].confidence == Decimal("0.95")
    fallback.extract.assert_awaited_once()


@pytest.mark.asyncio
async def test_cascade_empty_primary_fallback():
    cascade, primary, fallback = _cascade(
        primary_result=[], fallback_result=[_item("타이레놀정", "0.95")])
    result = await cascade.extract(b"img")
    assert len(result) == 1
    fallback.extract.assert_awaited_once()


@pytest.mark.asyncio
async def test_cascade_partial_quality_no_fallback():
    """일부만 저품질이면 fallback 안 함 (all-조건이라 1개라도 양품이면 primary 채택)."""
    cascade, primary, fallback = _cascade(
        primary_result=[_item("???", "0.1"), _item("타이레놀정", "0.95")])
    result = await cascade.extract(b"img")
    assert len(result) == 2
    fallback.extract.assert_not_awaited()


@pytest.mark.asyncio
async def test_cascade_primary_busy_error_fallback():
    """T-AI-OCR-LATENCY-30S — primary 재시도예산 소진(VisionBusyError) 도 flash fallback 트리거."""
    from app.exceptions import VisionBusyError

    cascade, primary, fallback = _cascade(
        primary_exc=VisionBusyError("vision retry budget exhausted"),
        fallback_result=[_item("타이레놀정", "0.95")])
    result = await cascade.extract(b"img")
    assert result[0].name_raw == "타이레놀정"
    fallback.extract.assert_awaited_once()


@pytest.mark.asyncio
async def test_cascade_both_fail_propagates():
    cascade, primary, fallback = _cascade(
        primary_exc=VisionInvocationError("lite fail"),
        fallback_exc=VisionInvocationError("flash fail"))
    with pytest.raises(VisionInvocationError):
        await cascade.extract(b"img")


class TestFactoryCascadeVariant:
    def _settings(self, variant="cascade", model="gemini-2.5-flash"):
        from types import SimpleNamespace
        return SimpleNamespace(
            gemini_model=model, ocr_vision_variant=variant,
            gemini_key_list=["AIzaTESTKEY1234"], ocr_fewshot_enabled=False)

    def test_factory_cascade_variant_returns_cascade_adapter(self):
        from app.main import _build_vision
        adapter = _build_vision(self._settings())
        assert isinstance(adapter, CascadeVisionAdapter)

    def test_factory_cascade_primary_timeout_15s(self):
        from app.main import _build_vision
        from app.rag.ocr.cascade_vision import CASCADE_PRIMARY_TIMEOUT_SEC
        adapter = _build_vision(self._settings())
        assert CASCADE_PRIMARY_TIMEOUT_SEC == 15.0
        assert adapter._primary._timeout == 15.0

    def test_lite_adapter_accepts_timeout_override(self):
        from unittest.mock import AsyncMock
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter
        adapter = GeminiVisionLiteAdapter(_llms=[AsyncMock()], timeout_sec=15.0)
        assert adapter._timeout == 15.0

    def test_resolve_variant_cascade_override(self):
        from app.main import _resolve_vision_variant
        assert _resolve_vision_variant(self._settings()) == "cascade"
