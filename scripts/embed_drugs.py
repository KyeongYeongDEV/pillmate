"""Gemini text-embedding-004 으로 drugs 테이블 벡터 적재.

원본: efficacy 또는 dosage 가 있는 ACTIVE 약품 중 아직 임베딩 안 된 행.
대상: drug_embeddings (drug_id PK, vector(768)).
배치 50, 일일 가드 MAX_EMBED_COUNT (기본 5000), 체크포인트 .drug_embed_checkpoint.json.
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s :: %(message)s",
)
logger = logging.getLogger("embed_drugs")

CHECKPOINT_PATH = ROOT / ".drug_embed_checkpoint.json"
EMBEDDING_DIM = 768
EMBEDDING_MODEL = "models/gemini-embedding-001"
BATCH_SIZE = 50
DEFAULT_MAX_EMBED_COUNT = 5000
RATE_LIMIT_RETRY_DELAYS = (1.0, 2.0, 4.0)
SLEEP_BETWEEN_BATCHES_SEC = 0.5


SELECT_SQL = """
SELECT d.id, d.name, d.ingredient, d.efficacy, d.dosage, d.main_ingr
FROM drugs d
LEFT JOIN drug_embeddings e ON e.drug_id = d.id
WHERE d.status = 'ACTIVE'
  AND (d.efficacy IS NOT NULL OR d.dosage IS NOT NULL)
  AND e.drug_id IS NULL
  AND d.id > %s
ORDER BY d.id
LIMIT %s
"""

UPSERT_SQL = """
INSERT INTO drug_embeddings (drug_id, embedding, embedded_at)
VALUES (%s, %s::vector, %s)
ON CONFLICT (drug_id) DO UPDATE
SET embedding = EXCLUDED.embedding,
    embedded_at = EXCLUDED.embedded_at
"""


def _connect_db():
    import psycopg

    return psycopg.connect(
        host=os.environ.get("POSTGRES_HOST", "localhost"),
        port=int(os.environ.get("POSTGRES_PORT", "5433")),
        dbname=os.environ.get("POSTGRES_DB", "pillmate"),
        user=os.environ.get("POSTGRES_USER", "pillmate"),
        password=os.environ.get("POSTGRES_PASSWORD", ""),
        autocommit=False,
    )


def _load_checkpoint() -> dict:
    if not CHECKPOINT_PATH.exists():
        return {"last_drug_id": 0, "done_count": 0}
    return json.loads(CHECKPOINT_PATH.read_text(encoding="utf-8"))


def _save_checkpoint(state: dict) -> None:
    CHECKPOINT_PATH.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def _build_text(row: dict) -> str:
    parts = [row["name"]]
    if row.get("main_ingr"):
        parts.append(row["main_ingr"])
    if row.get("ingredient"):
        parts.append(row["ingredient"])
    if row.get("efficacy"):
        parts.append(row["efficacy"])
    if row.get("dosage"):
        parts.append(row["dosage"])
    return " ".join(p for p in parts if p).strip()


def _format_vector(values: list[float]) -> str:
    return "[" + ",".join(f"{v:.8f}" for v in values) + "]"


def _embed_with_retry(embed_fn, texts: list[str]) -> list[list[float]]:
    delays = (*RATE_LIMIT_RETRY_DELAYS, None)
    last_exc: Exception | None = None
    for delay in delays:
        try:
            return embed_fn(texts)
        except Exception as exc:
            last_exc = exc
            if delay is None or not _is_rate_limit(exc):
                raise
            logger.warning("rate limited (%s); retry in %.1fs", exc, delay)
            time.sleep(delay)
    raise RuntimeError(f"rate limit retries exhausted: {last_exc}")


def _is_rate_limit(exc: Exception) -> bool:
    msg = str(exc).lower()
    return "429" in msg or "rate" in msg or "quota" in msg


def _fetch_batch(conn, last_id: int, limit: int) -> list[dict]:
    with conn.cursor() as cur:
        cur.execute(SELECT_SQL, (last_id, limit))
        columns = [d.name for d in cur.description]
        return [dict(zip(columns, row)) for row in cur.fetchall()]


def _upsert_batch(conn, drug_ids: Iterable[int], vectors: Iterable[list[float]]) -> None:
    now = datetime.now(timezone.utc)
    with conn.cursor() as cur:
        for drug_id, vector in zip(drug_ids, vectors):
            cur.execute(UPSERT_SQL, (drug_id, _format_vector(vector), now))


def _build_embed_fn():
    import google.generativeai as genai

    genai.configure(api_key=os.environ["GEMINI_API_KEY"])

    def embed(texts: list[str]) -> list[list[float]]:
        result = genai.embed_content(
            model=EMBEDDING_MODEL,
            content=texts,
            task_type="RETRIEVAL_DOCUMENT",
            output_dimensionality=EMBEDDING_DIM,
        )
        return result["embedding"]

    return embed


def run(args: argparse.Namespace) -> int:
    load_dotenv(ROOT / ".env")
    if not os.environ.get("GEMINI_API_KEY"):
        logger.error("GEMINI_API_KEY missing")
        return 2

    max_count = int(os.environ.get("MAX_EMBED_COUNT", DEFAULT_MAX_EMBED_COUNT))
    state = _load_checkpoint() if args.resume else {"last_drug_id": 0, "done_count": 0}
    last_id = int(state["last_drug_id"])
    done = int(state["done_count"])
    logger.info("resume=%s last_id=%d done=%d max=%d", args.resume, last_id, done, max_count)

    embed_fn = _build_embed_fn()
    conn = _connect_db()
    try:
        while done < max_count:
            batch = _fetch_batch(conn, last_id, BATCH_SIZE)
            if not batch:
                logger.info("no more rows to embed")
                break

            texts = [_build_text(row) for row in batch]
            vectors = _embed_with_retry(embed_fn, texts)
            assert all(len(v) == EMBEDDING_DIM for v in vectors), "embedding dim mismatch"

            _upsert_batch(conn, [row["id"] for row in batch], vectors)
            conn.commit()

            last_id = batch[-1]["id"]
            done += len(batch)
            _save_checkpoint({"last_drug_id": last_id, "done_count": done})
            logger.info("batch done=%d last_id=%d (+%d)", done, last_id, len(batch))
            time.sleep(SLEEP_BETWEEN_BATCHES_SEC)

        if done >= max_count:
            logger.warning("MAX_EMBED_COUNT=%d reached (done=%d), stopping", max_count, done)
        return 0
    finally:
        conn.close()


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="drugs → drug_embeddings (Gemini text-embedding-004)")
    p.add_argument("--resume", action="store_true", help="체크포인트에서 이어서")
    return p.parse_args(argv)


if __name__ == "__main__":
    sys.exit(run(parse_args()))
