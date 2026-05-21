"""
약 마스터 데이터 pgvector 임베딩 생성 스크립트

사용:
    python scripts/embed_drugs.py           # 임베딩 없는 것만
    python scripts/embed_drugs.py --all     # 전체 재생성

환경변수 (.env):
    GEMINI_API_KEY, POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
"""
from __future__ import annotations

import argparse
import os
import sys

import google.generativeai as genai
import psycopg
from dotenv import load_dotenv

load_dotenv()

BATCH_SIZE = 50
EMBED_MODEL = "models/text-embedding-004"
VECTOR_DIM = 768


def _dsn() -> str:
    return (
        f"host={os.getenv('POSTGRES_HOST', 'localhost')} "
        f"port={os.getenv('POSTGRES_PORT', '5433')} "
        f"dbname={os.getenv('POSTGRES_DB', 'pillmate')} "
        f"user={os.getenv('POSTGRES_USER', 'pillmate')} "
        f"password={os.getenv('POSTGRES_PASSWORD', 'pillmate_local')}"
    )


def get_unembedded_drug_ids() -> list[int]:
    with psycopg.connect(_dsn()) as conn:
        rows = conn.execute(
            """
            SELECT d.id FROM drugs d
            LEFT JOIN drug_embeddings e ON d.id = e.drug_id
            WHERE e.drug_id IS NULL AND d.status = 'ACTIVE'
            ORDER BY d.id
            """
        ).fetchall()
    return [r[0] for r in rows]


def get_all_drug_ids() -> list[int]:
    with psycopg.connect(_dsn()) as conn:
        rows = conn.execute(
            "SELECT id FROM drugs WHERE status = 'ACTIVE' ORDER BY id"
        ).fetchall()
    return [r[0] for r in rows]


def get_drug_texts(drug_ids: list[int]) -> list[tuple[int, str]]:
    with psycopg.connect(_dsn()) as conn:
        rows = conn.execute(
            """
            SELECT id,
                   name || ' ' || COALESCE(ingredient, '') || ' ' || COALESCE(efficacy, '')
            FROM drugs
            WHERE id = ANY(%s)
            """,
            (drug_ids,),
        ).fetchall()
    return [(r[0], r[1].strip()) for r in rows]


def embed_batch(texts: list[str]) -> list[list[float]]:
    result = genai.embed_content(
        model=EMBED_MODEL,
        content=texts,
        task_type="RETRIEVAL_DOCUMENT",
    )
    return result["embedding"]


def upsert_embeddings(drug_ids: list[int], vectors: list[list[float]]) -> None:
    rows = [(drug_id, f"[{','.join(map(str, vec))}]") for drug_id, vec in zip(drug_ids, vectors)]
    with psycopg.connect(_dsn()) as conn:
        conn.executemany(
            """
            INSERT INTO drug_embeddings (drug_id, embedding, embedded_at)
            VALUES (%s, %s::vector, NOW())
            ON CONFLICT (drug_id) DO UPDATE SET
                embedding   = EXCLUDED.embedding,
                embedded_at = NOW()
            """,
            rows,
        )
        conn.commit()


def main() -> int:
    parser = argparse.ArgumentParser(description="약 마스터 임베딩 생성")
    parser.add_argument("--all", action="store_true", help="전체 재생성")
    args = parser.parse_args()

    api_key = os.getenv("GEMINI_API_KEY", "")
    if not api_key:
        print("GEMINI_API_KEY 환경변수가 없습니다.", file=sys.stderr)
        return 1

    genai.configure(api_key=api_key)

    drug_ids = get_all_drug_ids() if args.all else get_unembedded_drug_ids()
    total = len(drug_ids)
    if total == 0:
        print("임베딩할 약이 없습니다.")
        return 0

    print(f"임베딩 대상: {total}건")

    for i in range(0, total, BATCH_SIZE):
        batch_ids = drug_ids[i : i + BATCH_SIZE]
        texts_data = get_drug_texts(batch_ids)
        ids = [t[0] for t in texts_data]
        texts = [t[1] for t in texts_data]

        vectors = embed_batch(texts)
        upsert_embeddings(ids, vectors)
        print(f"  {min(i + BATCH_SIZE, total)}/{total}건 완료", end="\r")

    print(f"\n임베딩 완료: {total}건")
    return 0


if __name__ == "__main__":
    sys.exit(main())
