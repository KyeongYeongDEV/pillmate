"""Tests for OcrMatchLogger — best-effort INSERT, failure tolerance, no patient info."""
from __future__ import annotations

import asyncpg
import pytest

from app.rag.ocr.match_logger import OcrMatchLogEntry, OcrMatchLogger


class _FakeConn:
    def __init__(self) -> None:
        self.calls: list[tuple] = []

    async def execute(self, sql: str, *args: object) -> None:
        self.calls.append((sql, args))


class _AcquireCtx:
    def __init__(self, conn: _FakeConn) -> None:
        self._conn = conn

    async def __aenter__(self) -> _FakeConn:
        return self._conn

    async def __aexit__(self, *a: object) -> None:
        pass


class _FakePool:
    def __init__(self, conn: _FakeConn) -> None:
        self._conn = conn

    def acquire(self) -> _AcquireCtx:
        return _AcquireCtx(self._conn)


@pytest.mark.asyncio
async def test_insert_calls_db_with_raw_ocr_text() -> None:
    conn = _FakeConn()
    log = OcrMatchLogger(_FakePool(conn))
    entry = OcrMatchLogEntry(
        image_hash="abc123",
        image_key="prescriptions/2026/uuid.jpg",
        raw_ocr_text="타이레놀",
        decision="AUTO",
        final_score=0.95,
    )
    await log.insert(entry)
    assert len(conn.calls) == 1
    assert "타이레놀" in str(conn.calls[0])


@pytest.mark.asyncio
async def test_insert_failure_is_silently_ignored() -> None:
    class _BrokenConn:
        async def execute(self, *a: object) -> None:
            raise RuntimeError("db down")

    log = OcrMatchLogger(_FakePool(_BrokenConn()))  # type: ignore[arg-type]
    entry = OcrMatchLogEntry(image_hash=None, image_key=None, raw_ocr_text="약", decision="MANUAL")
    await log.insert(entry)  # must not raise


@pytest.mark.asyncio
async def test_insert_undefined_table_is_silently_skipped() -> None:
    class _NoTableConn:
        async def execute(self, *a: object) -> None:
            raise asyncpg.UndefinedTableError("ocr_match_logs does not exist")

    log = OcrMatchLogger(_FakePool(_NoTableConn()))  # type: ignore[arg-type]
    entry = OcrMatchLogEntry(image_hash=None, image_key=None, raw_ocr_text="약", decision="MANUAL")
    await log.insert(entry)  # must not raise


@pytest.mark.asyncio
async def test_raw_ocr_text_truncated_to_300_chars() -> None:
    conn = _FakeConn()
    log = OcrMatchLogger(_FakePool(conn))
    long_name = "가" * 400
    entry = OcrMatchLogEntry(image_hash=None, image_key=None, raw_ocr_text=long_name)
    await log.insert(entry)
    _, args = conn.calls[0]
    # raw_ocr_text is the 3rd positional param ($3)
    assert len(args[2]) == 300


@pytest.mark.asyncio
async def test_entry_contains_no_patient_info() -> None:
    """환자 식별 정보(환자명, ID)가 entry 필드에 존재하지 않는다."""
    entry = OcrMatchLogEntry(
        image_hash="hash",
        image_key="key",
        raw_ocr_text="타이레놀",
        decision="AUTO",
        matched_kd_code="KD-001",
        matched_drug_name="타이레놀500mg",
    )
    fields = set(entry.__dataclass_fields__)
    assert "patient_id" not in fields
    assert "patient_name" not in fields
    assert "user_id" not in fields
