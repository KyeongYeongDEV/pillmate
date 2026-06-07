"""GT 데이터셋 로더 + eval fixture"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

GT_JSONL = Path(__file__).parent / "gt" / "prescriptions.jsonl"


@pytest.fixture(scope="session")
def gt_entries() -> list[dict]:
    return [json.loads(line) for line in GT_JSONL.read_text().splitlines() if line.strip()]


@pytest.fixture(scope="session")
def gt_by_difficulty(gt_entries: list[dict]) -> dict[str, list[dict]]:
    groups: dict[str, list[dict]] = {}
    for entry in gt_entries:
        d = entry.get("difficulty", "easy")
        groups.setdefault(d, []).append(entry)
    return groups


@pytest.fixture(scope="session")
def gt_by_stage_hint(gt_entries: list[dict]) -> dict[str, list[dict]]:
    groups: dict[str, list[dict]] = {}
    for entry in gt_entries:
        hint = entry["metadata"].get("stage_hint", "ilike")
        groups.setdefault(hint, []).append(entry)
    return groups
