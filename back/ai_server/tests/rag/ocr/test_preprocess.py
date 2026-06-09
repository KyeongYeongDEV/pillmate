"""
ImagePreprocessor 단위 테스트 — TDD RED

OpenCV 이미지 전처리:
  rotate_by_exif / deskew / enhance_contrast / denoise / resize_if_large / preprocess

cv2 및 PIL(Pillow) 사용. preprocess.py 구현 전 전부 RED.
"""
from __future__ import annotations

import io
import sys
from pathlib import Path

import cv2
import numpy as np
import pytest
from PIL import Image

ROOT = Path(__file__).resolve().parents[4]  # back/ai_server
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.rag.ocr.preprocess import ImagePreprocessor  # noqa: E402 — RED까지 ImportError


# ─────────────────────────────────────────────
# 헬퍼
# ─────────────────────────────────────────────

def _make_jpeg(h: int, w: int, fill: int = 128) -> bytes:
    """균일 색상의 JPEG bytes 생성 (EXIF 없음)."""
    arr = np.full((h, w, 3), fill, dtype=np.uint8)
    ok, buf = cv2.imencode(".jpg", arr, [cv2.IMWRITE_JPEG_QUALITY, 90])
    assert ok
    return bytes(buf)


def _make_jpeg_with_exif_orientation(h: int, w: int, orientation: int) -> bytes:
    """EXIF Orientation 태그가 포함된 JPEG bytes 생성 (PIL 사용)."""
    arr = np.full((h, w, 3), 128, dtype=np.uint8)
    img = Image.fromarray(arr, "RGB")
    exif = img.getexif()
    exif[274] = orientation  # 274 = Orientation tag
    buf = io.BytesIO()
    img.save(buf, format="JPEG", exif=exif.tobytes())
    return buf.getvalue()


def _decode_dims(jpeg_bytes: bytes) -> tuple[int, int]:
    """JPEG bytes → (height, width) 반환."""
    arr = cv2.imdecode(np.frombuffer(jpeg_bytes, dtype=np.uint8), cv2.IMREAD_COLOR)
    assert arr is not None
    return arr.shape[0], arr.shape[1]  # (h, w)


# ─────────────────────────────────────────────
# GROUP 1: rotate_by_exif (3건)
# ─────────────────────────────────────────────

class TestRotateByExif:
    def test_no_exif_jpeg_returns_bytes(self):
        # given — EXIF 없는 단순 JPEG
        image_bytes = _make_jpeg(100, 200)
        preprocessor = ImagePreprocessor()

        # when
        result = preprocessor.rotate_by_exif(image_bytes)

        # then
        assert isinstance(result, bytes)
        assert len(result) > 0

    def test_exif_orientation_1_returns_same_dims(self):
        # given — Orientation=1 (정상, 회전 불필요)
        image_bytes = _make_jpeg_with_exif_orientation(100, 200, orientation=1)
        preprocessor = ImagePreprocessor()

        # when
        result = preprocessor.rotate_by_exif(image_bytes)

        # then — 치수 유지
        h, w = _decode_dims(result)
        assert h == 100
        assert w == 200

    def test_exif_orientation_6_swaps_dimensions(self):
        # given — Orientation=6 (시계방향 90° 회전 필요)
        # 원본 100×200 → 회전 후 200×100
        image_bytes = _make_jpeg_with_exif_orientation(100, 200, orientation=6)
        preprocessor = ImagePreprocessor()

        # when
        result = preprocessor.rotate_by_exif(image_bytes)

        # then — width/height 스왑
        h, w = _decode_dims(result)
        assert h == 200
        assert w == 100


# ─────────────────────────────────────────────
# GROUP 2: deskew (3건)
# ─────────────────────────────────────────────

class TestDeskew:
    def test_deskew_returns_bytes(self):
        # given
        image_bytes = _make_jpeg(200, 400)
        preprocessor = ImagePreprocessor()

        # when
        result = preprocessor.deskew(image_bytes)

        # then
        assert isinstance(result, bytes)
        assert len(result) > 0

    def test_deskew_straight_image_preserves_approx_dims(self):
        # given — 수평선이 많은 격자형 이미지 (skew ≈ 0°)
        arr = np.full((300, 600, 3), 200, dtype=np.uint8)
        for y in range(20, 300, 30):
            arr[y, :] = 50  # 수평 줄
        ok, buf = cv2.imencode(".jpg", arr)
        image_bytes = bytes(buf)
        preprocessor = ImagePreprocessor()

        # when
        result = preprocessor.deskew(image_bytes)

        # then — 치수가 크게 변하지 않음 (±20%)
        h_in, w_in = _decode_dims(image_bytes)
        h_out, w_out = _decode_dims(result)
        assert abs(h_out - h_in) <= h_in * 0.2
        assert abs(w_out - w_in) <= w_in * 0.2

    def test_deskew_with_high_threshold_returns_original(self):
        # given — skew_threshold 매우 높게 설정 → 보정 건너뜀
        image_bytes = _make_jpeg(200, 400)
        preprocessor = ImagePreprocessor(skew_threshold_deg=89.0)

        # when
        result = preprocessor.deskew(image_bytes)

        # then — bytes 반환 (원본과 동일하거나 무손실에 가까움)
        assert isinstance(result, bytes)
        assert len(result) > 0


# ─────────────────────────────────────────────
# GROUP 3: enhance_contrast (3건)
# ─────────────────────────────────────────────

class TestEnhanceContrast:
    def test_enhance_contrast_returns_bytes(self):
        # given
        image_bytes = _make_jpeg(200, 200)
        preprocessor = ImagePreprocessor()

        # when
        result = preprocessor.enhance_contrast(image_bytes)

        # then
        assert isinstance(result, bytes)
        assert len(result) > 0

    def test_enhance_contrast_preserves_shape(self):
        # given
        image_bytes = _make_jpeg(100, 300)
        preprocessor = ImagePreprocessor()

        # when
        result = preprocessor.enhance_contrast(image_bytes)

        # then — 치수 변경 없음
        h, w = _decode_dims(result)
        assert h == 100
        assert w == 300

    def test_enhance_contrast_increases_stddev_on_uniform_dark_image(self):
        # given — 매우 어두운 균일 이미지 (stddev ≈ 0 → CLAHE 후 ↑)
        arr = np.full((200, 200, 3), 20, dtype=np.uint8)
        ok, buf = cv2.imencode(".jpg", arr)
        image_bytes = bytes(buf)
        preprocessor = ImagePreprocessor()

        # when
        result = preprocessor.enhance_contrast(image_bytes)

        # then — 결과의 픽셀 표준편차가 원본보다 크거나 같음 (CLAHE 효과)
        arr_out = cv2.imdecode(np.frombuffer(result, np.uint8), cv2.IMREAD_GRAYSCALE)
        arr_in = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_GRAYSCALE)
        assert float(arr_out.std()) >= float(arr_in.std())


# ─────────────────────────────────────────────
# GROUP 4: denoise (2건)
# ─────────────────────────────────────────────

class TestDenoise:
    def test_denoise_returns_bytes(self):
        # given
        image_bytes = _make_jpeg(200, 200)
        preprocessor = ImagePreprocessor()

        # when
        result = preprocessor.denoise(image_bytes)

        # then
        assert isinstance(result, bytes)
        assert len(result) > 0

    def test_denoise_preserves_shape(self):
        # given
        image_bytes = _make_jpeg(150, 350)
        preprocessor = ImagePreprocessor()

        # when
        result = preprocessor.denoise(image_bytes)

        # then
        h, w = _decode_dims(result)
        assert h == 150
        assert w == 350


# ─────────────────────────────────────────────
# GROUP 5: resize_if_large (3건)
# ─────────────────────────────────────────────

class TestResizeIfLarge:
    def test_large_image_downscaled_to_max(self):
        # given — 3840×2160 이미지
        image_bytes = _make_jpeg(2160, 3840)
        preprocessor = ImagePreprocessor(max_width=1920, max_height=1080)

        # when
        result = preprocessor.resize_if_large(image_bytes)

        # then — 최대 치수 이하
        h, w = _decode_dims(result)
        assert h <= 1080
        assert w <= 1920

    def test_small_image_not_resized(self):
        # given — 640×480 (최대치 이하)
        image_bytes = _make_jpeg(480, 640)
        preprocessor = ImagePreprocessor(max_width=1920, max_height=1080)

        # when
        result = preprocessor.resize_if_large(image_bytes)

        # then — 치수 유지
        h, w = _decode_dims(result)
        assert h == 480
        assert w == 640

    def test_resize_preserves_aspect_ratio(self):
        # given — 3840×2160 (16:9)
        image_bytes = _make_jpeg(2160, 3840)
        preprocessor = ImagePreprocessor(max_width=1920, max_height=1080)

        # when
        result = preprocessor.resize_if_large(image_bytes)

        # then — 가로세로 비율 유지 (±5%)
        h, w = _decode_dims(result)
        original_ratio = 3840 / 2160
        result_ratio = w / h
        assert abs(result_ratio - original_ratio) / original_ratio < 0.05


# ─────────────────────────────────────────────
# GROUP 6: preprocess 전체 파이프라인 (1건)
# ─────────────────────────────────────────────

class TestPreprocessPipeline:
    def test_preprocess_full_pipeline_returns_bytes(self):
        # given — 단순 JPEG
        image_bytes = _make_jpeg(800, 600)
        preprocessor = ImagePreprocessor()

        # when
        result = preprocessor.preprocess(image_bytes)

        # then
        assert isinstance(result, bytes)
        assert len(result) > 0
        # 디코딩 가능한 유효한 이미지 bytes
        arr = cv2.imdecode(np.frombuffer(result, dtype=np.uint8), cv2.IMREAD_COLOR)
        assert arr is not None
