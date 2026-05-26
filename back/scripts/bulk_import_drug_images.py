"""식약처 약 이미지를 S3에 일괄 다운로드·업로드 후 drugs.image_s3_key UPDATE.

흐름:
    1. checkpoint 읽기 — 이미 처리된 kd_code 스킵
    2. SELECT kd_code, item_image WHERE image_s3_key IS NULL AND item_image IS NOT NULL
    3. 4 asyncio worker 병렬:
         식약처 GET → S3 PUT → UPDATE drugs SET image_s3_key = ? WHERE kd_code = ?
    4. 실패 시 failures.jsonl 기록 + 다음 진행
    5. 5 RPS 글로벌 rate limit (asyncio.Semaphore)

사용:
    python scripts/bulk_import_drug_images.py
    python scripts/bulk_import_drug_images.py --dry-run
    python scripts/bulk_import_drug_images.py --limit 100
    python scripts/bulk_import_drug_images.py --resume
    python scripts/bulk_import_drug_images.py --concurrency 4
"""
from __future__ import annotations

import argparse
import asyncio
import gc
import json
import logging
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import asyncpg
import boto3
import httpx
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s :: %(message)s",
)
logger = logging.getLogger("bulk_import_drug_images")

CHECKPOINT_PATH = ROOT / ".drug_image_cache_checkpoint.json"
FAILURES_PATH = ROOT / ".drug_image_cache_failures.jsonl"
S3_KEY_PREFIX = "drugs/images"
CONTENT_TYPE = "image/jpeg"
MAX_IMAGE_BYTES = 5 * 1024 * 1024  # 5 MB
REQUEST_TIMEOUT = httpx.Timeout(connect=10.0, read=30.0, write=10.0, pool=10.0)
DEFAULT_CONCURRENCY = int(os.environ.get("PILLMATE_DRUG_IMG_WORKERS", "2"))
RPS_LIMIT = 5  # 식약처 5 RPS
CHECKPOINT_FLUSH_INTERVAL = 100  # N건마다 checkpoint 저장 + GC

SELECT_SQL = """
SELECT kd_code, item_image
FROM drugs
WHERE image_s3_key IS NULL
  AND item_image IS NOT NULL
ORDER BY kd_code
"""

UPDATE_SQL = "UPDATE drugs SET image_s3_key = $1 WHERE kd_code = $2"


def _dsn() -> str:
    return (
        f"postgresql://{os.environ.get('POSTGRES_USER', 'pillmate')}"
        f":{os.environ.get('POSTGRES_PASSWORD', 'pillmate_local')}"
        f"@{os.environ.get('POSTGRES_HOST', 'localhost')}"
        f":{os.environ.get('POSTGRES_PORT', '5433')}"
        f"/{os.environ.get('POSTGRES_DB', 'pillmate')}"
    )


def _s3_client() -> Any:
    return boto3.client(
        "s3",
        region_name=os.environ.get("AWS_REGION", "ap-northeast-2"),
        aws_access_key_id=os.environ.get("AWS_ACCESS_KEY_ID"),
        aws_secret_access_key=os.environ.get("AWS_SECRET_ACCESS_KEY"),
    )


def load_checkpoint() -> set[str]:
    if CHECKPOINT_PATH.exists():
        data = json.loads(CHECKPOINT_PATH.read_text())
        return set(data.get("done_kd_codes", []))
    return set()


def save_checkpoint(done: set[str]) -> None:
    CHECKPOINT_PATH.write_text(json.dumps(
        {"done_kd_codes": sorted(done),
         "count": len(done),
         "saved_at": datetime.now(timezone.utc).isoformat()},
        ensure_ascii=False))


def append_failure(record: dict[str, Any]) -> None:
    with FAILURES_PATH.open("a") as fh:
        fh.write(json.dumps(record, ensure_ascii=False) + "\n")


async def fetch_image(client: httpx.AsyncClient, url: str) -> bytes | None:
    for attempt in range(3):
        try:
            resp = await client.get(url, follow_redirects=True, timeout=REQUEST_TIMEOUT)
            if resp.status_code >= 400:
                return None
            content_type = resp.headers.get("content-type", "")
            if not content_type.startswith("image/"):
                return None
            data = resp.content
            if len(data) > MAX_IMAGE_BYTES:
                return None
            return data
        except (httpx.TimeoutException, httpx.NetworkError):
            if attempt < 2:
                await asyncio.sleep(2 ** attempt)
    return None


def upload_to_s3(s3: Any, bucket: str, key: str, data: bytes) -> None:
    s3.put_object(
        Bucket=bucket,
        Key=key,
        Body=data,
        ContentType=CONTENT_TYPE,
        ServerSideEncryption="AES256",
    )


async def process_one(
    rps_lock: asyncio.Lock,
    http: httpx.AsyncClient,
    pool: asyncpg.Pool | None,
    s3: Any,
    bucket: str,
    kd_code: str,
    item_image: str,
    dry_run: bool,
) -> bool:
    async with rps_lock:
        await asyncio.sleep(1.0 / RPS_LIMIT)

    try:
        image_data = await fetch_image(http, item_image)
        if image_data is None:
            append_failure({"kd_code": kd_code, "url": item_image, "reason": "download_failed"})
            return False

        s3_key = f"{S3_KEY_PREFIX}/{kd_code}.jpg"

        if not dry_run:
            await asyncio.get_event_loop().run_in_executor(
                None, upload_to_s3, s3, bucket, s3_key, image_data)
            async with pool.acquire() as conn:
                await conn.execute(UPDATE_SQL, s3_key, kd_code)

        return True
    except Exception as exc:
        append_failure({"kd_code": kd_code, "url": item_image, "reason": str(exc)})
        logger.warning("처리 실패 kd_code=%s: %s", kd_code, exc)
        return False


async def run_async(args: argparse.Namespace) -> int:
    bucket = os.environ.get("S3_BUCKET_NAME", "pillmate-prescriptions")
    done_codes = load_checkpoint() if args.resume else set()

    pool = await asyncpg.create_pool(_dsn(), min_size=1, max_size=args.concurrency + 2) \
        if not args.dry_run else None
    s3 = _s3_client() if not args.dry_run else None

    try:
        query_conn = await asyncpg.connect(_dsn())
        rows = await query_conn.fetch(SELECT_SQL)
        await query_conn.close()

        pending = [r for r in rows if r["kd_code"] not in done_codes]
        if args.limit:
            pending = pending[:args.limit]

        logger.info("처리 대상: %d건 (checkpoint skip: %d)", len(pending), len(done_codes))

        rps_lock = asyncio.Lock()
        sem = asyncio.Semaphore(args.concurrency)

        async def bounded(row: asyncpg.Record) -> bool:
            async with sem:
                return await process_one(
                    rps_lock, http, pool, s3, bucket,
                    row["kd_code"], row["item_image"], args.dry_run)

        success = 0
        batch_start = 0
        async with httpx.AsyncClient() as http:
            while batch_start < len(pending):
                batch = pending[batch_start: batch_start + CHECKPOINT_FLUSH_INTERVAL]
                results = await asyncio.gather(*[bounded(r) for r in batch])
                for i, ok in enumerate(results):
                    if ok:
                        done_codes.add(batch[i]["kd_code"])
                        success += 1
                batch_start += CHECKPOINT_FLUSH_INTERVAL
                if not args.dry_run:
                    save_checkpoint(done_codes)
                    gc.collect()
                total_target = len(pending) + len(done_codes)
                pct = len(done_codes) / total_target * 100 if total_target else 0
                logger.info("배치완료: 누적=%d / 전체대상=%d (%.1f%%)", len(done_codes), total_target, pct)

        failures_count = sum(1 for _ in open(FAILURES_PATH)) if FAILURES_PATH.exists() else 0
        logger.info("완료: 성공=%d 실패=%d 누적=%d", success, len(pending) - success, len(done_codes))
        logger.info("failures.jsonl 라인 수: %d", failures_count)

    finally:
        if pool:
            await pool.close()

    return 0


def main() -> None:
    parser = argparse.ArgumentParser(description="식약처 약 이미지 S3 일괄 캐시")
    parser.add_argument("--dry-run", action="store_true", help="DB/S3 적재 없이 파싱만")
    parser.add_argument("--limit", type=int, default=0, help="N건 처리 후 종료")
    parser.add_argument("--resume", action="store_true", help="checkpoint에서 이어서")
    parser.add_argument("--concurrency", type=int, default=DEFAULT_CONCURRENCY, help="병렬 worker 수")
    args = parser.parse_args()
    sys.exit(asyncio.run(run_async(args)))


if __name__ == "__main__":
    main()
