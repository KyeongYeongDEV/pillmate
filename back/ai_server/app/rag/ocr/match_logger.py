"""best-effort asyncpg INSERT into ocr_match_logs after each drug match."""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from decimal import Decimal

import asyncpg

logger = logging.getLogger(__name__)

MATCHER_VERSION = "rrf-v1"
_ABS_THRESHOLD = Decimal("0.70")

_INSERT_SQL = """
INSERT INTO ocr_match_logs (
    image_hash, image_key, raw_ocr_text, normalized_query,
    matched_kd_code, matched_drug_name, decision, final_score,
    rrf_score, reranker_score, surfaced_by, candidates_json,
    matcher_version, threshold, gemini_raw_json, latency_ms
) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16)
"""


@dataclass
class OcrMatchLogEntry:
    image_hash: str | None
    image_key: str | None
    raw_ocr_text: str
    normalized_query: str | None = None
    matched_kd_code: str | None = None
    matched_drug_name: str | None = None
    decision: str | None = None
    final_score: float | None = None
    rrf_score: float | None = None
    reranker_score: float | None = None
    surfaced_by: str | None = None
    candidates_json: str | None = None
    matcher_version: str = MATCHER_VERSION
    threshold: float = float(_ABS_THRESHOLD)
    gemini_raw_json: str | None = None
    latency_ms: int | None = None


class OcrMatchLogger:
    def __init__(self, pool: asyncpg.Pool) -> None:
        self._pool = pool

    async def insert(self, entry: OcrMatchLogEntry) -> None:
        """best-effort — never raises; silently skips on any error."""
        try:
            async with self._pool.acquire() as conn:
                await conn.execute(
                    _INSERT_SQL,
                    entry.image_hash,
                    entry.image_key,
                    entry.raw_ocr_text[:300],
                    entry.normalized_query[:300] if entry.normalized_query else None,
                    entry.matched_kd_code,
                    entry.matched_drug_name,
                    entry.decision,
                    entry.final_score,
                    entry.rrf_score,
                    entry.reranker_score,
                    entry.surfaced_by,
                    entry.candidates_json,
                    entry.matcher_version,
                    entry.threshold,
                    entry.gemini_raw_json,
                    entry.latency_ms,
                )
        except asyncpg.UndefinedTableError:
            pass  # table not yet created by Flyway — graceful skip
        except Exception as exc:
            logger.warning("ocr_match_log insert failed (best-effort): %s", exc)
