from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal
from typing import Any

import jamotools
import Levenshtein

JAMO_DISTANCE_THRESHOLD = 3
TRGM_SIMILARITY_THRESHOLD = 0.3

_CHOSUNG = list("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ")

_TRGM_SQL = """
    SELECT kd_code, name, name_jamo,
           similarity(name, $1) AS trgm_score
    FROM drugs
    WHERE status = 'ACTIVE' AND name % $1
    ORDER BY name <-> $1
    LIMIT 50
"""


@dataclass
class FuzzyCandidate:
    kd_code: str
    name: str
    name_jamo: str
    trgm_score: float
    jamo_distance: int = field(default=0, compare=False)
    jamo_score: float = field(default=0.0, compare=False)


class TrigramFuzzySearch:
    """1단계: DB GIN 1차 필터로 Top-N 후보 추출."""

    def __init__(self, pool: Any) -> None:
        self._pool = pool

    async def search(self, query_name: str) -> list[FuzzyCandidate]:
        async with self._pool.acquire() as conn:
            rows = await conn.fetch(_TRGM_SQL, query_name)
        return [
            FuzzyCandidate(
                kd_code=row["kd_code"],
                name=row["name"],
                name_jamo=row["name_jamo"] or jamotools.split_syllables(row["name"]),
                trgm_score=float(row["trgm_score"]),
            )
            for row in rows
        ]


class JamoFuzzyRanker:
    """2단계: Python Levenshtein 자모 편집거리 재측정 + 임계치 필터."""

    def rerank(
        self, query_jamo: str, candidates: list[FuzzyCandidate]
    ) -> list[FuzzyCandidate]:
        scored: list[FuzzyCandidate] = []
        for c in candidates:
            dist = Levenshtein.distance(query_jamo, c.name_jamo)
            c.jamo_distance = dist
            c.jamo_score = max(0.0, 1.0 - dist / max(len(query_jamo), 1))
            if dist <= JAMO_DISTANCE_THRESHOLD:
                scored.append(c)
        return sorted(scored, key=lambda x: -x.jamo_score)


def to_chosung(name: str) -> str:
    """이름 → 초성. '메트포르민' → 'ㅁㅌㅍㄹㅁ'. 단독 결정 X — RRF 보조 신호."""
    return "".join(
        _CHOSUNG[(ord(c) - 0xAC00) // (21 * 28)]
        for c in name
        if "가" <= c <= "힣"
    )
