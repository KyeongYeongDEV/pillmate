"""
drug_embeddings 47K 전체 임베딩 확장 스크립트

모든 ACTIVE 약품 (name 전용 포함) → text-embedding-3-small 768d → drug_embeddings INSERT.

사용법:
  # dry-run (100건 비용 측정):
  python bulk_embed_drugs.py --dry-run --limit 100

  # 전체 실행:
  python bulk_embed_drugs.py --resume

  # 처음부터:
  python bulk_embed_drugs.py

db-safety: ON CONFLICT DO NOTHING (INSERT only, UPDATE X)
cost-aware: text-embedding-3-small $0.02/1M tokens (~$0.09/47K)
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
from typing import Iterable, Iterator

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent.parent.parent  # back/
if str(ROOT / "ai_server") not in sys.path:
    sys.path.insert(0, str(ROOT / "ai_server"))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s :: %(message)s",
)
logger = logging.getLogger("bulk_embed_drugs")

CHECKPOINT_PATH = ROOT / ".drug_embed_checkpoint.json"
FAILURE_LOG_PATH = ROOT / ".drug_embed_failures.jsonl"

EMBEDDING_DIM = 768
EMBEDDING_MODEL = "text-embedding-3-small"
BATCH_SIZE = 100
RATE_LIMIT_RETRY_DELAYS = (1.0, 2.0, 4.0, 8.0, 16.0)
SLEEP_BETWEEN_BATCHES_SEC = 0.3

# $0.02 / 1,000,000 tokens
COST_PER_TOKEN = 0.02 / 1_000_000

SELECT_SQL = """
SELECT d.id, d.name, d.ingredient, d.efficacy, d.dosage
FROM drugs d
LEFT JOIN drug_embeddings e ON e.drug_id = d.id
WHERE d.status = 'ACTIVE'
  AND e.drug_id IS NULL
  AND d.id > %s
ORDER BY d.id
LIMIT %s
"""

# db-safety: ON CONFLICT DO NOTHING (INSERT only)
INSERT_SQL = """
INSERT INTO drug_embeddings (drug_id, embedding, embedded_at)
VALUES (%s, %s::vector, %s)
ON CONFLICT (drug_id) DO NOTHING
"""


def _build_text(row: dict) -> str:
    """약품 row → 임베딩 텍스트. name 은 항상 포함, 나머지는 있을 때만."""
    parts = [row["name"]]
    for field in ("ingredient", "efficacy", "dosage"):
        val = row.get(field)
        if val and str(val).strip():
            parts.append(str(val).strip())
    return " ".join(parts)


def _batch_chunks(items: list, size: int) -> Iterator[list]:
    for i in range(0, len(items), size):
        yield items[i : i + size]


def _load_checkpoint(path: Path = CHECKPOINT_PATH) -> dict:
    if not path.exists():
        return {"last_drug_id": 0, "done_count": 0}
    return json.loads(path.read_text(encoding="utf-8"))


def _save_checkpoint(state: dict, path: Path = CHECKPOINT_PATH) -> None:
    path.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def _log_failure(drug_id: int, name: str, error: str, path: Path = FAILURE_LOG_PATH) -> None:
    entry = json.dumps({"drug_id": drug_id, "name": name, "error": error}, ensure_ascii=False)
    with path.open("a", encoding="utf-8") as f:
        f.write(entry + "\n")


def _format_vector(values: list[float]) -> str:
    return "[" + ",".join(f"{v:.8f}" for v in values) + "]"


def _is_rate_limit(exc: Exception) -> bool:
    msg = str(exc).lower()
    return "429" in msg or "rate" in msg or "quota" in msg


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
            logger.warning("rate limited (%s); retry in %.1fs", exc.__class__.__name__, delay)
            time.sleep(delay)
    raise RuntimeError(f"rate limit retries exhausted: {last_exc}")


def estimate_cost(item_count: int, avg_tokens_per_item: int = 50) -> float:
    """임베딩 예상 비용 (USD). text-embedding-3-small $0.02/1M tokens."""
    total_tokens = item_count * avg_tokens_per_item
    return total_tokens * COST_PER_TOKEN


def _resolve_openai_key() -> str:
    return (
        os.environ.get("OPENAI_API_KEY")
        or os.environ.get("OpenAI_API_KEY")
        or ""
    )


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


def _build_embed_fn(api_key: str):
    from openai import OpenAI

    client = OpenAI(api_key=api_key)

    def embed(texts: list[str]) -> list[list[float]]:
        response = client.embeddings.create(
            model=EMBEDDING_MODEL,
            input=texts,
            dimensions=EMBEDDING_DIM,
        )
        return [item.embedding for item in response.data]

    return embed


def _fetch_batch(conn, last_id: int, limit: int) -> list[dict]:
    with conn.cursor() as cur:
        cur.execute(SELECT_SQL, (last_id, limit))
        columns = [d.name for d in cur.description]
        return [dict(zip(columns, row)) for row in cur.fetchall()]


def _insert_batch(conn, drug_ids: list[int], vectors: list[list[float]]) -> int:
    now = datetime.now(timezone.utc)
    inserted = 0
    with conn.cursor() as cur:
        for drug_id, vector in zip(drug_ids, vectors):
            cur.execute(INSERT_SQL, (drug_id, _format_vector(vector), now))
            inserted += cur.rowcount
    return inserted


def _count_remaining(conn) -> int:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT COUNT(*) FROM drugs d LEFT JOIN drug_embeddings e ON e.drug_id=d.id "
            "WHERE d.status='ACTIVE' AND e.drug_id IS NULL"
        )
        return cur.fetchone()[0]


def run(args: argparse.Namespace) -> int:
    load_dotenv(ROOT / ".env")

    api_key = _resolve_openai_key()
    if not api_key:
        logger.error("OPENAI_API_KEY (or OpenAI_API_KEY) env var missing")
        return 2

    remaining_before = None
    conn = _connect_db()
    try:
        remaining_before = _count_remaining(conn)
        logger.info("drug_embeddings 미완성: %d건", remaining_before)

        if args.dry_run:
            limit = args.limit or 100
            est_cost = estimate_cost(remaining_before)
            logger.info("[DRY-RUN] limit=%d | 전체 예상 비용: $%.4f", limit, est_cost)
            return _run_dry(conn, api_key, limit)

        state = _load_checkpoint() if args.resume else {"last_drug_id": 0, "done_count": 0}
        return _run_full(conn, api_key, state, limit=args.limit)
    finally:
        conn.close()


def _run_dry(conn, api_key: str, limit: int) -> int:
    """dry-run: limit 건만 임베딩 후 비용/결과 측정. DB 에 INSERT 안 함."""
    embed_fn = _build_embed_fn(api_key)
    batch = _fetch_batch(conn, 0, limit)
    if not batch:
        logger.info("[DRY-RUN] 임베딩 대상 없음")
        return 0

    texts = [_build_text(row) for row in batch]
    total_chars = sum(len(t) for t in texts)
    est_tokens = total_chars // 4
    est_cost = estimate_cost(len(texts), avg_tokens_per_item=est_tokens // max(len(texts), 1))

    logger.info("[DRY-RUN] %d건 텍스트 준비 완료 (총 %d chars, 예상 토큰 ~%d)", len(texts), total_chars, est_tokens)

    try:
        vectors = _embed_with_retry(embed_fn, texts)
        assert all(len(v) == EMBEDDING_DIM for v in vectors), f"dim mismatch: {[len(v) for v in vectors]}"
        logger.info("[DRY-RUN] ✓ %d건 임베딩 성공 | 예상 비용: $%.5f (dry-run 비율 기준)", len(vectors), est_cost)
        full_est = est_cost * (42285 / max(len(batch), 1))
        logger.info("[DRY-RUN] 전체 42,285건 예상 비용: $%.4f", full_est)
        return 0
    except Exception as exc:
        logger.error("[DRY-RUN] 임베딩 실패: %s", exc)
        return 1


def _run_full(conn, api_key: str, state: dict, limit: int | None = None) -> int:
    embed_fn = _build_embed_fn(api_key)
    last_id = int(state["last_drug_id"])
    done = int(state["done_count"])
    max_count = limit or 999_999

    logger.info("full run: last_id=%d done=%d max=%d", last_id, done, max_count)

    total_inserted = 0
    total_failed = 0

    while done < max_count:
        batch = _fetch_batch(conn, last_id, BATCH_SIZE)
        if not batch:
            logger.info("임베딩 완료 — 남은 대상 없음")
            break

        texts = [_build_text(row) for row in batch]
        ids = [row["id"] for row in batch]

        try:
            vectors = _embed_with_retry(embed_fn, texts)
        except Exception as exc:
            logger.error("batch embed failed (ids %d~%d): %s", ids[0], ids[-1], exc)
            for row in batch:
                _log_failure(row["id"], row["name"], str(exc))
            total_failed += len(batch)
            last_id = ids[-1]
            done += len(batch)
            _save_checkpoint({"last_drug_id": last_id, "done_count": done})
            time.sleep(SLEEP_BETWEEN_BATCHES_SEC)
            continue

        inserted = _insert_batch(conn, ids, vectors)
        conn.commit()

        last_id = ids[-1]
        done += len(batch)
        total_inserted += inserted
        _save_checkpoint({"last_drug_id": last_id, "done_count": done})
        logger.info("done=%d last_id=%d inserted=%d failed=%d", done, last_id, total_inserted, total_failed)
        time.sleep(SLEEP_BETWEEN_BATCHES_SEC)

    logger.info("완료 — inserted=%d failed=%d", total_inserted, total_failed)
    return 0 if total_failed == 0 else 1


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="drugs 47K → drug_embeddings (text-embedding-3-small 768d)"
    )
    p.add_argument("--dry-run", action="store_true", help="DB INSERT 없이 비용 측정만")
    p.add_argument("--resume", action="store_true", help="체크포인트에서 이어서")
    p.add_argument("--limit", type=int, default=None, help="처리할 최대 건수")
    return p.parse_args(argv)


if __name__ == "__main__":
    sys.exit(run(parse_args()))
