"""
이미지 전처리 — Phase B-6

OCR raw 정확도 향상을 위한 Gemini Vision 호출 전 전처리 파이프라인.
PREPROCESS_ENABLED feature flag 로 on/off 가능.

처리 순서:
  rotate_by_exif → deskew → resize_if_large → enhance_contrast → denoise
"""
from __future__ import annotations

import io
import logging
import math

import cv2
import numpy as np
from PIL import Image, ImageOps

logger = logging.getLogger(__name__)

_DEFAULT_MAX_WIDTH = 1920
_DEFAULT_MAX_HEIGHT = 1080
_DEFAULT_CLAHE_CLIP = 2.0
_DEFAULT_CLAHE_TILE = 8
_DEFAULT_BILATERAL_D = 9
_DEFAULT_BILATERAL_SIGMA = 75.0
_DEFAULT_SKEW_THRESHOLD = 1.0  # degree — 이 이하면 보정 skip


class ImagePreprocessor:
    """
    약봉투·처방전 이미지 전처리기.

    Gemini Vision 호출 전에 이미지 품질을 개선해 OCR 정확도 ↑.
    각 단계는 독립적으로 사용 가능. preprocess() 로 전체 파이프라인 실행.
    """

    def __init__(
        self,
        max_width: int = _DEFAULT_MAX_WIDTH,
        max_height: int = _DEFAULT_MAX_HEIGHT,
        clahe_clip_limit: float = _DEFAULT_CLAHE_CLIP,
        clahe_tile_grid: int = _DEFAULT_CLAHE_TILE,
        bilateral_d: int = _DEFAULT_BILATERAL_D,
        bilateral_sigma_color: float = _DEFAULT_BILATERAL_SIGMA,
        bilateral_sigma_space: float = _DEFAULT_BILATERAL_SIGMA,
        skew_threshold_deg: float = _DEFAULT_SKEW_THRESHOLD,
    ) -> None:
        self._max_width = max_width
        self._max_height = max_height
        self._clahe = cv2.createCLAHE(
            clipLimit=clahe_clip_limit,
            tileGridSize=(clahe_tile_grid, clahe_tile_grid),
        )
        self._bilateral_d = bilateral_d
        self._bilateral_sigma_color = bilateral_sigma_color
        self._bilateral_sigma_space = bilateral_sigma_space
        self._skew_threshold_deg = skew_threshold_deg

    # ──────────────────────────────────────────
    # 공개 인터페이스
    # ──────────────────────────────────────────

    def preprocess(self, image_bytes: bytes) -> bytes:
        """전체 파이프라인 실행: EXIF 회전 → deskew → resize → CLAHE → denoise."""
        step = image_bytes
        step = self.rotate_by_exif(step)
        step = self.deskew(step)
        step = self.resize_if_large(step)
        step = self.enhance_contrast(step)
        step = self.denoise(step)
        return step

    def rotate_by_exif(self, image_bytes: bytes) -> bytes:
        """EXIF Orientation 태그 기준 자동 회전. 태그 없으면 원본 반환."""
        try:
            with Image.open(io.BytesIO(image_bytes)) as img:
                rotated = ImageOps.exif_transpose(img)
                if rotated is img:
                    return image_bytes
                buf = io.BytesIO()
                fmt = img.format or "JPEG"
                rotated.save(buf, format=fmt)
                return buf.getvalue()
        except Exception as exc:
            logger.debug("rotate_by_exif skipped: %s", exc)
            return image_bytes

    def deskew(self, image_bytes: bytes) -> bytes:
        """Hough 라인 기반 skew 보정. |angle| < threshold 이면 원본 반환."""
        arr = _decode(image_bytes)
        angle = _detect_skew_angle(arr)
        if abs(angle) < self._skew_threshold_deg:
            return image_bytes
        rotated = _rotate_image(arr, angle)
        return _encode_jpeg(rotated)

    def enhance_contrast(self, image_bytes: bytes) -> bytes:
        """CLAHE 대비 향상 (L 채널에 적용 후 원래 색공간 복원)."""
        arr = _decode(image_bytes)
        lab = cv2.cvtColor(arr, cv2.COLOR_BGR2LAB)
        l_ch, a_ch, b_ch = cv2.split(lab)
        l_enhanced = self._clahe.apply(l_ch)
        merged = cv2.merge([l_enhanced, a_ch, b_ch])
        result = cv2.cvtColor(merged, cv2.COLOR_LAB2BGR)
        return _encode_jpeg(result)

    def denoise(self, image_bytes: bytes) -> bytes:
        """bilateral filter 디노이즈 (경계선 보존, 노이즈 제거)."""
        arr = _decode(image_bytes)
        denoised = cv2.bilateralFilter(
            arr,
            self._bilateral_d,
            self._bilateral_sigma_color,
            self._bilateral_sigma_space,
        )
        return _encode_jpeg(denoised)

    def resize_if_large(self, image_bytes: bytes) -> bytes:
        """이미지가 max_width×max_height 초과 시 비율 유지하며 축소."""
        arr = _decode(image_bytes)
        h, w = arr.shape[:2]
        if w <= self._max_width and h <= self._max_height:
            return image_bytes
        scale = min(self._max_width / w, self._max_height / h)
        new_w = int(w * scale)
        new_h = int(h * scale)
        resized = cv2.resize(arr, (new_w, new_h), interpolation=cv2.INTER_AREA)
        return _encode_jpeg(resized)


# ──────────────────────────────────────────
# 내부 헬퍼
# ──────────────────────────────────────────

def _decode(image_bytes: bytes) -> np.ndarray:
    arr = cv2.imdecode(np.frombuffer(image_bytes, dtype=np.uint8), cv2.IMREAD_COLOR)
    if arr is None:
        raise ValueError("cv2.imdecode failed — invalid image bytes")
    return arr


def _encode_jpeg(arr: np.ndarray) -> bytes:
    ok, buf = cv2.imencode(".jpg", arr, [cv2.IMWRITE_JPEG_QUALITY, 92])
    if not ok:
        raise ValueError("cv2.imencode failed")
    return bytes(buf)


def _detect_skew_angle(arr: np.ndarray) -> float:
    """
    Hough 라인으로 기울기 추정.
    텍스트 라인이 많은 처방전 이미지에 적합.
    추정 불가 시 0.0 반환.
    """
    gray = cv2.cvtColor(arr, cv2.COLOR_BGR2GRAY)
    blurred = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blurred, 50, 150)
    lines = cv2.HoughLines(edges, rho=1, theta=np.pi / 180, threshold=100)
    if lines is None or len(lines) == 0:
        return 0.0

    angles = []
    for line in lines:
        rho, theta = line[0]
        angle_deg = math.degrees(theta) - 90.0
        if abs(angle_deg) <= 45:
            angles.append(angle_deg)

    if not angles:
        return 0.0

    angles_arr = np.array(angles)
    return float(np.median(angles_arr))


def _rotate_image(arr: np.ndarray, angle_deg: float) -> np.ndarray:
    h, w = arr.shape[:2]
    cx, cy = w // 2, h // 2
    matrix = cv2.getRotationMatrix2D((cx, cy), angle_deg, 1.0)
    return cv2.warpAffine(
        arr,
        matrix,
        (w, h),
        flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_REPLICATE,
    )
