"""낱알식별 외관 정보 도메인 모델 (Tier 5 fallback)."""
from __future__ import annotations

from pydantic import BaseModel, ConfigDict


class PillAppearance(BaseModel):
    """Vision이 약 이미지에서 추출한 외관 정보."""

    model_config = ConfigDict(extra="ignore")

    shape: str | None = None
    color: str | None = None
    mark_front: str | None = None
    mark_back: str | None = None
    line: bool | None = None
