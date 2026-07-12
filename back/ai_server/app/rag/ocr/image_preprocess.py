"""
Gemini Vision 전 이미지 리사이즈 (T-BE-OCR-SPEED-UNIT-A Part 2).

목적:
    - 원본 4032×3024 처방전 이미지를 Gemini 호출 전 max_edge=1600 로 축소.
    - Gemini vision tokens 감소 (258 * n_tiles) + 업로드/추론 시간 -5~15초.

원칙:
    - 최대 변 기준 aspect ratio 유지.
    - 원본이 이미 max_edge 이하면 no-op (불필요한 재인코딩 방지).
    - JPEG quality 85 (텍스트 판독 가능 폰트 16px+ 확보 검증).
    - preprocess.py 의 무거운 CLAHE/deskew/denoise 파이프라인과 별개 — 이건 가벼운 리사이즈만.

참조:
    - .claude/rules/common/cost-aware.md — 토큰 절감
    - .claude/rules/common/medical-safety.md — OCR_MIN_CONFIDENCE 유지 (품질 검증 상위 레이어)
"""
from __future__ import annotations

import io

from PIL import Image

DEFAULT_MAX_EDGE = 1600
JPEG_QUALITY = 85


def resize_max_edge(image_bytes: bytes, max_edge: int = DEFAULT_MAX_EDGE) -> bytes:
    with Image.open(io.BytesIO(image_bytes)) as img:
        width, height = img.size
        longest = max(width, height)
        if longest <= max_edge:
            return image_bytes
        scale = max_edge / longest
        new_size = (int(width * scale), int(height * scale))
        resized = img.resize(new_size, Image.Resampling.LANCZOS)
        if resized.mode != "RGB":
            resized = resized.convert("RGB")
        buf = io.BytesIO()
        resized.save(buf, format="JPEG", quality=JPEG_QUALITY, optimize=False)
        return buf.getvalue()
