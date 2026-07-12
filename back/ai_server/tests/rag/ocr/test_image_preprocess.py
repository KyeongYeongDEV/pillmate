"""resize_max_edge 유닛 테스트 — T-BE-OCR-SPEED-UNIT-A Part 2."""
from __future__ import annotations

import io

import pytest
from PIL import Image

from app.rag.ocr.image_preprocess import DEFAULT_MAX_EDGE, resize_max_edge


def _jpeg_bytes(width: int, height: int) -> bytes:
    img = Image.new("RGB", (width, height), color=(200, 200, 200))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=95)
    return buf.getvalue()


class TestResizeMaxEdge:
    def test_no_op_when_within_max_edge(self):
        original = _jpeg_bytes(1200, 900)
        result = resize_max_edge(original, max_edge=1600)
        assert result is original

    def test_downscales_landscape_to_max_edge_1600(self):
        original = _jpeg_bytes(4032, 3024)
        result = resize_max_edge(original)
        with Image.open(io.BytesIO(result)) as img:
            assert max(img.size) == DEFAULT_MAX_EDGE
            assert img.size == (1600, 1200)

    def test_downscales_portrait_preserving_aspect_ratio(self):
        original = _jpeg_bytes(3024, 4032)
        result = resize_max_edge(original, max_edge=1600)
        with Image.open(io.BytesIO(result)) as img:
            assert max(img.size) == 1600
            assert img.size == (1200, 1600)

    def test_returns_jpeg_bytes_after_downscale(self):
        original = _jpeg_bytes(4032, 3024)
        result = resize_max_edge(original)
        with Image.open(io.BytesIO(result)) as img:
            assert img.format == "JPEG"

    def test_exact_max_edge_is_no_op(self):
        original = _jpeg_bytes(1600, 1200)
        result = resize_max_edge(original, max_edge=1600)
        assert result is original

    @pytest.mark.parametrize("max_edge", [800, 1200, 2000])
    def test_custom_max_edge_respected(self, max_edge: int):
        original = _jpeg_bytes(4000, 2000)
        result = resize_max_edge(original, max_edge=max_edge)
        with Image.open(io.BytesIO(result)) as img:
            assert max(img.size) == max_edge
