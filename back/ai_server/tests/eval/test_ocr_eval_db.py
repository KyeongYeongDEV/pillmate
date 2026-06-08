"""
DB 연결 OCR 평가 — pytest -m eval

ingredient stage 실제 DB 연결 후 Hard Hit@1 개선 측정.
hit 판정: 반환된 약품명이 GT 약품의 주성분명(INN)을 포함하는지 확인.
"""
from __future__ import annotations

import json
import os
import re
from pathlib import Path

import asyncpg
import pytest

from tests.eval.metrics import EvalResult, summarize

GT_JSONL = Path(__file__).parent / "gt" / "prescriptions.jsonl"
REPORT_DIR = Path(__file__).parent.parent.parent / "reports" / "eval"
POSTGRES_DSN = os.getenv(
    "POSTGRES_DSN",
    "postgresql://pillmate:pillmate_local@localhost:5433/pillmate",
)

_HARD_INN: dict[str, str] = {
    "gt_031": "세티리진",
    "gt_032": "암로디핀",
    "gt_033": "오메프라졸",
    "gt_034": "메트포르민",
    "gt_035": "로수바스타틴",
    "gt_071": "타이레놀",
    "gt_072": "아목시실린",
    "gt_073": "이부프로펜",
    "gt_074": "아스피린",
    "gt_075": "메트포르민",
    "gt_076": "암로디핀",
    "gt_077": "로수바스타틴",
    "gt_078": "오메프라졸",
    "gt_079": "세티리진",
    "gt_080": "클래리스로마이신",
    "gt_086": "가바펜틴",
    "gt_087": "졸피뎀",
    "gt_088": "로페라미드",
    "gt_089": "글리메피리",
    "gt_090": "모사프리드",
    "gt_092": "에소메프라졸",
    "gt_096": "이부프로펜",
    "gt_097": "졸피뎀",
    "gt_098": "아목시실린",
    "gt_099": "메트포르민",
    "gt_100": "아목시실린",
}


_UNIT_TOKENS = frozenset({"mg", "ml", "mcg", "ug", "g", "iu", "mg/ml"})


def _extract_search_term(name_raw: str) -> str:
    """영문 단어를 추출하되, 단위(mg/ml/g 등)는 무시하고 한국어 전체를 반환."""
    for m in re.finditer(r"[A-Za-z]+", name_raw):
        if m.group(0).lower() not in _UNIT_TOKENS:
            return m.group(0)
    return name_raw.strip()


def _is_hit(returned_name: str | None, gt_id: str) -> bool:
    if not returned_name:
        return False
    inn = _HARD_INN.get(gt_id, "")
    return bool(inn) and inn in returned_name


def _load_hard_entries() -> list[dict]:
    return [
        e
        for e in (
            json.loads(line)
            for line in GT_JSONL.read_text().splitlines()
            if line.strip()
        )
        if e.get("difficulty") == "hard"
    ]


async def _make_pool() -> asyncpg.Pool:
    return await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=2)


async def _eval_entries(pool, entries: list[dict]) -> list[EvalResult]:
    from app.rag.ocr.drug_search import AsyncpgIngredientSearch

    searcher = AsyncpgIngredientSearch(pool)
    results = []
    for entry in entries:
        term = _extract_search_term(entry["name_raw"])
        candidate = await searcher.search(term)
        matched_name = candidate.name if candidate else None
        hit = _is_hit(matched_name, entry["id"])
        gt_kd = entry["drugs"][0]["kd_code"]
        results.append(
            EvalResult(
                gt_id=entry["id"],
                gt_kd_code=gt_kd,
                name_raw=entry["name_raw"],
                retrieved_kd_codes=[gt_kd] if hit else ["KD_MISS"],
                stage="ingredient_db",
                matched=hit,
                rank=1 if hit else None,
                difficulty=entry.get("difficulty", "hard"),
                extra={
                    "matched_name": matched_name,
                    "stage_hint": entry["metadata"].get("stage_hint"),
                },
            )
        )
    return results


@pytest.mark.eval
class TestDbConnectedOcrEval:
    async def test_hard_cases_ingredient_hit_rate(self):
        pool = await _make_pool()
        try:
            hard = _load_hard_entries()
            assert len(hard) == 26
            results = await _eval_entries(pool, hard)
        finally:
            await pool.close()

        hits = sum(1 for r in results if r.matched)
        rate = hits / len(results)
        for r in results:
            if not r.matched:
                print(f"  MISS {r.gt_id}: {r.name_raw!r} → {r.extra.get('matched_name')!r}")
        print(f"\n[DB] Hard ingredient Hit@1: {hits}/{len(results)} = {rate:.3f}")
        assert rate >= 0.60, f"Hard Hit@1 {rate:.3f} — 목표 0.60+ 미달"

    async def test_report_json_saved(self):
        pool = await _make_pool()
        try:
            hard = _load_hard_entries()
            results = await _eval_entries(pool, hard)
        finally:
            await pool.close()

        summary = summarize(results, by_difficulty=True)
        REPORT_DIR.mkdir(parents=True, exist_ok=True)
        out = REPORT_DIR / "post_db_connect_2026-06-08.json"
        out.write_text(json.dumps(summary, ensure_ascii=False, indent=2))
        assert out.exists()
        loaded = json.loads(out.read_text())
        assert loaded["total"] == 26

    async def test_english_inn_hit_rate(self):
        english_ids = {
            "gt_071", "gt_072", "gt_073", "gt_074", "gt_075",
            "gt_076", "gt_077", "gt_078", "gt_079", "gt_080",
            "gt_086", "gt_087", "gt_088", "gt_089", "gt_090",
        }
        pool = await _make_pool()
        try:
            entries = [e for e in _load_hard_entries() if e["id"] in english_ids]
            results = await _eval_entries(pool, entries)
        finally:
            await pool.close()

        hits = sum(1 for r in results if r.matched)
        rate = hits / len(results)
        print(f"\n[DB] 영문 INN 15건 Hit@1: {hits}/{len(results)} = {rate:.3f}")
        assert rate >= 0.60, f"영문 INN hit rate {rate:.3f} — 목표 0.60+ 미달"

    async def test_korean_category_hit_rate(self):
        category_ids = {
            "gt_031", "gt_032", "gt_033", "gt_034", "gt_035",
            "gt_096", "gt_097", "gt_098", "gt_099", "gt_100",
        }
        pool = await _make_pool()
        try:
            entries = [e for e in _load_hard_entries() if e["id"] in category_ids]
            results = await _eval_entries(pool, entries)
        finally:
            await pool.close()

        hits = sum(1 for r in results if r.matched)
        rate = hits / len(results)
        print(f"\n[DB] 한국어 카테고리 10건 Hit@1: {hits}/{len(results)} = {rate:.3f}")
        assert rate >= 0.50, f"카테고리 hit rate {rate:.3f} — 목표 0.50+ 미달"
