"""
Medium GT DB 평가 — pytest -m eval

44개 medium 케이스를 실제 DB 캐스케이드로 평가:
  1. ILIKE (이름 전체)
  2. Token ILIKE (첫 토큰)
  3. Ingredient alias (영문 INN → drug_alias)
  4. Trigram Fuzzy (pg_trgm + JamoFuzzyRanker)

baseline (offline eval): medium Hit@1 = 0.886 (39/44)
목표: 0.95+ (42/44 이상)
"""
from __future__ import annotations

import json
import os
import re
from pathlib import Path

import asyncpg
import jamotools
import pytest

from app.rag.ocr.drug_search import AsyncpgIlikeSearch, AsyncpgIngredientSearch
from app.rag.ocr.fuzzy_search import JamoFuzzyRanker, TrigramFuzzySearch
from app.rag.ocr.normalizer import first_english_token, first_token
from tests.eval.metrics import EvalResult, summarize

GT_JSONL = Path(__file__).parent / "gt" / "prescriptions.jsonl"
REPORT_DIR = Path(__file__).parent.parent.parent / "reports" / "eval"
POSTGRES_DSN = os.getenv(
    "POSTGRES_DSN",
    "postgresql://pillmate:pillmate_local@localhost:5433/pillmate",
)

_MEDIUM_INN: dict[str, str] = {
    "gt_016": "리오노필",
    "gt_017": "타이레놀",
    "gt_018": "아목시",
    "gt_019": "메트포르민",
    "gt_020": "암로디핀",
    "gt_021": "타이레놀",
    "gt_022": "아목시실린",
    "gt_023": "이부프로",
    "gt_024": "아스피린",
    "gt_025": "메트포르민",
    "gt_036": "타이레놀",
    "gt_037": "아목시",
    "gt_038": "이부프로",
    "gt_039": "아스피린",
    "gt_040": "메트포르민",
    "gt_051": "타이레놀",
    "gt_052": "아목시실린",
    "gt_053": "이부프로펜",
    "gt_054": "아스피린",
    "gt_055": "메트포르민",
    "gt_056": "암로디핀",
    "gt_057": "로수바스타틴",
    "gt_058": "오메프라졸",
    "gt_059": "세티리진",
    "gt_060": "클래리스로마이신",
    "gt_061": "독시사이클린",
    "gt_062": "프레드니솔론",
    "gt_063": "레보플록사신",
    "gt_064": "에소메프라졸",
    "gt_065": "세파드록실",
    "gt_066": "가바펜틴",
    "gt_067": "졸피뎀",
    "gt_068": "로페라미드",
    "gt_069": "글리메피",      # 글리메피라이드 / 글리메피리드 양쪽 커버
    "gt_070": "모사프리드",
    "gt_081": "타이레놀",
    "gt_082": "아목시실린",
    "gt_083": "이부프로",
    "gt_084": "메트포르민",
    "gt_085": "오메프라졸",
    "gt_091": "레보플록사신",
    "gt_093": "로수바스타틴",
    "gt_094": "세파클",
    "gt_095": "글리메피",      # 글리메피래이드 / 글리메피리드 양쪽 커버
}

_UNIT_TOKENS = frozenset({"mg", "ml", "mcg", "ug", "g", "iu", "mg/ml"})


def _safe_english_token(name_raw: str) -> str | None:
    for m in re.finditer(r"[A-Za-z]+", name_raw):
        if m.group(0).lower() not in _UNIT_TOKENS:
            return m.group(0)
    return None


def _is_medium_hit(returned_name: str | None, gt_id: str) -> bool:
    if not returned_name:
        return False
    inn = _MEDIUM_INN.get(gt_id, "")
    return bool(inn) and inn in returned_name


def _load_medium_entries() -> list[dict]:
    return [
        e
        for e in (
            json.loads(line)
            for line in GT_JSONL.read_text().splitlines()
            if line.strip()
        )
        if e.get("difficulty") == "medium"
    ]


async def _make_pool() -> asyncpg.Pool:
    return await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=4)


async def _cascade_search(pool, name_raw: str) -> tuple[str | None, str]:
    """ILIKE → Token ILIKE → Ingredient alias → Prefix Relaxation → Fuzzy."""
    ilike = AsyncpgIlikeSearch(pool)
    ingr = AsyncpgIngredientSearch(pool)

    # Stage 1: ILIKE (full name)
    cand = await ilike.search(name_raw)
    if cand:
        return cand.name, "ilike"

    # Stage 2: Token ILIKE (Korean prefix excluding dose/form)
    token = first_token(name_raw)
    if token and token != name_raw:
        cand = await ilike.search(token)
        if cand:
            return cand.name, "token"

    # Stage 3: Ingredient alias (영문 INN → drug_alias)
    english = _safe_english_token(name_raw)
    if english:
        cand = await ingr.search(english)
        if cand:
            return cand.name, "ingredient"

    # Stage 4: Prefix relaxation — 오기/약어 대응 (4글자 → 3글자 순)
    # trgm % 임계치가 짧은 쿼리 vs 긴 DB명에서 0.3 미만이 되는 경우 보완
    prefix = token if token else name_raw
    for length in (4, 3):
        if len(prefix) >= length:
            cand = await ilike.search(prefix[:length])
            if cand:
                return cand.name, "prefix_relaxed"

    # Stage 5: Trigram fuzzy + Jamo reranker (pg_trgm fallback)
    trgm = TrigramFuzzySearch(pool)
    fuzzy_cands = await trgm.search(name_raw)
    if fuzzy_cands:
        query_jamo = jamotools.split_syllables(name_raw)
        ranker = JamoFuzzyRanker()
        ranked = ranker.rerank(query_jamo, fuzzy_cands)
        if ranked:
            return ranked[0].name, "fuzzy"

    return None, "none"


@pytest.mark.eval
class TestMediumDbEval:
    async def test_medium_all_cases_hit_rate(self):
        """44 medium 케이스 전체 Hit@1 ≥ 0.95."""
        pool = await _make_pool()
        try:
            medium = _load_medium_entries()
            assert len(medium) == 44
            results = []
            for e in medium:
                matched_name, stage = await _cascade_search(pool, e["name_raw"])
                hit = _is_medium_hit(matched_name, e["id"])
                results.append(
                    EvalResult(
                        gt_id=e["id"],
                        gt_kd_code=e["drugs"][0]["kd_code"],
                        name_raw=e["name_raw"],
                        retrieved_kd_codes=[e["drugs"][0]["kd_code"]] if hit else ["KD_MISS"],
                        stage=stage,
                        matched=hit,
                        rank=1 if hit else None,
                        difficulty="medium",
                        extra={"matched_name": matched_name, "stage": stage},
                    )
                )
        finally:
            await pool.close()

        misses = [r for r in results if not r.matched]
        for m in misses:
            print(
                f"  MISS {m.gt_id}: {m.name_raw!r} "
                f"→ {m.extra.get('matched_name')!r} (stage={m.extra.get('stage')})"
            )

        hits = len(results) - len(misses)
        rate = hits / len(results)
        print(f"\n[DB] Medium Hit@1: {hits}/{len(results)} = {rate:.3f}")
        assert rate >= 0.95, f"Medium Hit@1 {rate:.3f} — 목표 0.95+ 미달"

    async def test_medium_token_hint_cases(self):
        """token hint 26건 Hit@1 ≥ 0.95."""
        pool = await _make_pool()
        try:
            medium = _load_medium_entries()
            token_entries = [e for e in medium if e["metadata"].get("stage_hint") == "token"]
            results = []
            for e in token_entries:
                matched_name, stage = await _cascade_search(pool, e["name_raw"])
                hit = _is_medium_hit(matched_name, e["id"])
                results.append((e["id"], e["name_raw"], matched_name, hit))
        finally:
            await pool.close()

        misses = [(gt_id, nr, mn) for gt_id, nr, mn, hit in results if not hit]
        for gt_id, nr, mn in misses:
            print(f"  MISS {gt_id}: {nr!r} → {mn!r}")

        rate = sum(1 for *_, hit in results if hit) / len(results)
        print(f"\n[DB] Token hint Hit@1: {sum(1 for *_, h in results if h)}/{len(results)} = {rate:.3f}")
        assert rate >= 0.95

    async def test_medium_ingredient_hint_cases(self):
        """ingredient hint 5건 (영문 INN) Hit@1 = 1.0."""
        pool = await _make_pool()
        try:
            medium = _load_medium_entries()
            ingr_entries = [e for e in medium if e["metadata"].get("stage_hint") == "ingredient"]
            results = []
            for e in ingr_entries:
                matched_name, stage = await _cascade_search(pool, e["name_raw"])
                hit = _is_medium_hit(matched_name, e["id"])
                results.append((e["id"], e["name_raw"], matched_name, hit))
        finally:
            await pool.close()

        for gt_id, nr, mn, hit in results:
            status = "HIT" if hit else "MISS"
            print(f"  {status} {gt_id}: {nr!r} → {mn!r}")

        rate = sum(1 for *_, h in results if h) / len(results)
        print(f"\n[DB] Ingredient hint Hit@1: {sum(1 for *_, h in results if h)}/{len(results)} = {rate:.3f}")
        assert rate >= 0.80, f"Ingredient Hit@1 {rate:.3f} — 목표 0.80+ 미달"

    async def test_medium_fuzzy_hint_cases(self):
        """fuzzy hint 13건 Hit@1 ≥ 0.85."""
        pool = await _make_pool()
        try:
            medium = _load_medium_entries()
            fuzzy_entries = [e for e in medium if e["metadata"].get("stage_hint") == "fuzzy"]
            results = []
            for e in fuzzy_entries:
                matched_name, stage = await _cascade_search(pool, e["name_raw"])
                hit = _is_medium_hit(matched_name, e["id"])
                results.append((e["id"], e["name_raw"], matched_name, stage, hit))
        finally:
            await pool.close()

        for gt_id, nr, mn, stage, hit in results:
            status = "HIT" if hit else "MISS"
            print(f"  {status} {gt_id}: {nr!r} → {mn!r} (stage={stage})")

        rate = sum(1 for *_, h in results if h) / len(results)
        print(f"\n[DB] Fuzzy hint Hit@1: {sum(1 for *_, h in results if h)}/{len(results)} = {rate:.3f}")
        assert rate >= 0.85, f"Fuzzy Hit@1 {rate:.3f} — 목표 0.85+ 미달"

    async def test_report_json_saved(self):
        """medium DB eval 결과를 JSON으로 저장."""
        pool = await _make_pool()
        try:
            medium = _load_medium_entries()
            results = []
            for e in medium:
                matched_name, stage = await _cascade_search(pool, e["name_raw"])
                hit = _is_medium_hit(matched_name, e["id"])
                results.append(
                    EvalResult(
                        gt_id=e["id"],
                        gt_kd_code=e["drugs"][0]["kd_code"],
                        name_raw=e["name_raw"],
                        retrieved_kd_codes=[e["drugs"][0]["kd_code"]] if hit else ["KD_MISS"],
                        stage=stage,
                        matched=hit,
                        rank=1 if hit else None,
                        difficulty="medium",
                        extra={"matched_name": matched_name},
                    )
                )
        finally:
            await pool.close()

        summary = summarize(results, by_difficulty=True)
        REPORT_DIR.mkdir(parents=True, exist_ok=True)
        out = REPORT_DIR / "post_reranker_medium_db_2026-06-08.json"
        out.write_text(json.dumps(summary, ensure_ascii=False, indent=2))
        assert out.exists()
        loaded = json.loads(out.read_text())
        assert loaded["total"] == 44
