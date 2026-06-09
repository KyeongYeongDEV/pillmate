"""낱알식별 fallback 어댑터 — Tier 5 (shape/color/mark → DB 매칭)."""
from __future__ import annotations

import logging
from decimal import Decimal
from typing import Any

from app.domain.pill_appearance import PillAppearance
from app.rag.ocr.matcher import MatchCandidate

logger = logging.getLogger(__name__)

_PILL_IDENTIFY_SCORE = Decimal("0.55")

_SQL = """
SELECT kd_code, name
FROM drugs
WHERE status = 'ACTIVE'
  AND shape_class = $1
  AND ($2 IS NULL OR color_class ILIKE $2)
  AND ($3 IS NULL OR mark_code_front ILIKE $3 OR mark_code_back ILIKE $3)
ORDER BY
  CASE
    WHEN $3 IS NOT NULL AND (mark_code_front ILIKE $3 OR mark_code_back ILIKE $3) THEN 0
    WHEN $2 IS NOT NULL AND color_class ILIKE $2 THEN 1
    ELSE 2
  END,
  length(name)
LIMIT $4
"""


class PillIdentifyAdapter:
    """낱알식별 정보(외관)로 DB 내 약 후보를 조회하는 어댑터."""

    def __init__(self, pool: Any, limit: int = 5) -> None:
        self._pool = pool
        self._limit = limit

    async def identify(self, appearance: PillAppearance) -> list[MatchCandidate]:
        if not appearance.shape:
            return []

        color_param = f"%{appearance.color}%" if appearance.color else None
        mark_param = f"%{appearance.mark_front}%" if appearance.mark_front else None

        try:
            async with self._pool.acquire() as conn:
                rows = await conn.fetch(
                    _SQL,
                    appearance.shape,
                    color_param,
                    mark_param,
                    self._limit,
                )
        except Exception as exc:
            logger.warning("pill_identify DB error: %s", exc.__class__.__name__)
            return []

        candidates = [
            MatchCandidate(kd_code=row["kd_code"], name=row["name"], score=_PILL_IDENTIFY_SCORE)
            for row in rows
            if row["kd_code"] and row["name"]
        ]
        if candidates:
            logger.info(
                "pill_identify shape=%s color=%s mark=%s → %d candidates",
                appearance.shape, appearance.color, appearance.mark_front, len(candidates),
            )
        return candidates
