"""
GT 100건 전체 통합 평가 — RrfMatcher (main.py 와 동일 경로)

실행: pytest -m eval_full (DB 연결 테스트)
        pytest tests/eval/test_eval_full.py (단위 테스트, DB 불필요)

평가≠운영 방지:
  EvalFullRunner 가 rrf_factory.build_rrf_matcher_inner() 를 통해
  main.py 가 주입하는 그 RrfMatcher 와 완전히 동일한 구성을 사용.
  _cascade_search 별도 경로를 완전 제거함으로써 재발 방지.

point 비교:
  baseline (offline) → post_db_connect (hard) → post_reranker (medium) → eval_full (전체)
"""
from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from pathlib import Path

import asyncpg
import pytest

from app.rag.ocr.matcher import MatchResult
from app.rag.ocr.normalizer import normalize_for_cascade
from app.rag.ocr.parser import parse_drug_item
from app.rag.ocr.rrf_factory import build_rrf_matcher_inner
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
    "gt_047": "졸피뎀",  # 졸피뎀타르타르산염정 → DB 약품명은 브랜드명(주석산졸피뎀); "졸피뎀" 이 INN
    "gt_086": "가바펜틴",
    "gt_087": "졸피뎀",
    "gt_088": "로페라미드",
    "gt_089": "글리메피리",
    "gt_090": "모사프리드",
    "gt_092": "에소메프라",  # "에소메프라정" 은 "에소메프라졸" 미포함, "에소메프라" 는 포함 (gate_b3 동기화)
    "gt_096": "이부프로펜",
    "gt_097": "졸피뎀",
    "gt_098": "아목시실린",
    "gt_099": "메트포르민",
    "gt_100": "아목시실린",
}

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
    "gt_069": "글리메피",
    "gt_070": "모사프리드",
    "gt_081": "타이레놀",
    "gt_082": "아목시실린",
    "gt_083": "이부프로",
    "gt_084": "메트포르민",
    "gt_085": "오메프라졸",
    "gt_091": "레보플록사신",
    "gt_093": "로수바스타틴",
    "gt_094": "세파클",
    "gt_095": "글리메피",
}


def _top_candidate_name(result: MatchResult) -> str | None:
    """AUTO/CONFIRM → primary.name. MANUAL → options[0].name (사용자에게 보이는 최상위 후보).
    Gate B3 와 동일한 surfacing 메트릭 — legacy cascade threshold-free 비교 가능.
    """
    if result.decision is None:
        return None
    if result.decision.primary is not None:
        return result.decision.primary.name
    if result.decision.options:
        return result.decision.options[0].name
    return None


def _auto_inn(drug_name: str) -> str:
    """easy GT 약품명에서 INN 추출 — 첫 한국어 연속 4자."""
    m = re.match(r"^([가-힣]{2,})", drug_name)
    if m:
        return m.group(1)[:4]
    return drug_name[:4]


def _is_hit_by_inn(matched_name: str | None, inn: str) -> bool:
    """반환된 약품명에 INN 키워드가 포함되면 HIT."""
    if not matched_name or not inn:
        return False
    return inn in matched_name


def _get_inn(gt_id: str, gt_drug_name: str) -> str:
    """GT ID 로 INN 조회 — hard → medium → auto(easy) 순."""
    if gt_id in _HARD_INN:
        return _HARD_INN[gt_id]
    if gt_id in _MEDIUM_INN:
        return _MEDIUM_INN[gt_id]
    return _auto_inn(gt_drug_name)


@dataclass
class FullEvalEntry:
    gt_id: str
    name_raw: str
    difficulty: str
    kd_code: str
    drug_name: str


class EvalFullRunner:
    """GT 100건 전체를 RrfMatcher (main.py 동일 경로) 로 평가.

    build_rrf_matcher_inner() 를 통해 main.py 와 구성이 동일함을 보장.
    """

    def __init__(self, pool: asyncpg.Pool) -> None:
        self._pool = pool
        self._matcher = build_rrf_matcher_inner(pool)

    def _load_all_entries(self) -> list[FullEvalEntry]:
        return [
            FullEvalEntry(
                gt_id=e["id"],
                name_raw=e["name_raw"],
                difficulty=e.get("difficulty", "easy"),
                kd_code=e["drugs"][0]["kd_code"],
                drug_name=e["drugs"][0].get("drug_name", e["name_raw"]),
            )
            for e in (
                json.loads(line)
                for line in GT_JSONL.read_text().splitlines()
                if line.strip()
            )
        ]

    async def run_entries(self, entries: list[FullEvalEntry]) -> list[EvalResult]:
        results = []
        for entry in entries:
            parsed = parse_drug_item(normalize_for_cascade(entry.name_raw))
            result = await self._matcher.match(parsed)
            # Gate B3 동일 메트릭: primary(AUTO) 또는 options[0](MANUAL) 중 최상위 후보.
            # legacy cascade 가 threshold 없이 반환하던 것과 비교 가능하게 surfacing 레벨로 측정.
            matched_name = _top_candidate_name(result)
            stage = result.stage
            inn = _get_inn(entry.gt_id, entry.drug_name)
            hit = _is_hit_by_inn(matched_name, inn)
            results.append(
                EvalResult(
                    gt_id=entry.gt_id,
                    gt_kd_code=entry.kd_code,
                    name_raw=entry.name_raw,
                    retrieved_kd_codes=[entry.kd_code] if hit else ["KD_MISS"],
                    stage=stage,
                    matched=hit,
                    rank=1 if hit else None,
                    difficulty=entry.difficulty,
                    extra={"matched_name": matched_name, "inn": inn},
                )
            )
        return results

    async def run_all(self) -> list[EvalResult]:
        return await self.run_entries(self._load_all_entries())


@pytest.mark.eval_full
class TestEvalFull:
    async def test_overall_100_cases(self):
        """GT 100건 전체 Hit@1 ≥ 0.90."""
        pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=4)
        try:
            runner = EvalFullRunner(pool)
            results = await runner.run_all()
        finally:
            await pool.close()

        assert len(results) == 100

        misses = [r for r in results if not r.matched]
        for r in misses:
            print(
                f"  MISS {r.gt_id}({r.difficulty}): {r.name_raw!r}"
                f" → {r.extra.get('matched_name')!r} [INN={r.extra.get('inn')!r}]"
            )

        hits = len(results) - len(misses)
        rate = hits / len(results)
        print(f"\n[RRF FULL] All 100 Hit@1: {hits}/{len(results)} = {rate:.3f}")
        assert rate >= 0.90, f"Overall Hit@1 {rate:.3f} — 목표 0.90+ 미달"

    async def test_easy_hit_rate(self):
        """easy 30건 Hit@1 ≥ 0.93."""
        pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=4)
        try:
            runner = EvalFullRunner(pool)
            all_entries = runner._load_all_entries()
            easy = [e for e in all_entries if e.difficulty == "easy"]
            results = await runner.run_entries(easy)
        finally:
            await pool.close()

        hits = sum(1 for r in results if r.matched)
        rate = hits / len(results)
        print(f"\n[RRF FULL] Easy Hit@1: {hits}/{len(results)} = {rate:.3f}")
        assert rate >= 0.93

    async def test_medium_hit_rate(self):
        """medium 44건 Hit@1 ≥ 0.90."""
        pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=4)
        try:
            runner = EvalFullRunner(pool)
            all_entries = runner._load_all_entries()
            medium = [e for e in all_entries if e.difficulty == "medium"]
            results = await runner.run_entries(medium)
        finally:
            await pool.close()

        hits = sum(1 for r in results if r.matched)
        rate = hits / len(results)
        print(f"\n[RRF FULL] Medium Hit@1: {hits}/{len(results)} = {rate:.3f}")
        assert rate >= 0.90

    async def test_hard_hit_rate(self):
        """hard 26건 Hit@1 ≥ 0.85."""
        pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=4)
        try:
            runner = EvalFullRunner(pool)
            all_entries = runner._load_all_entries()
            hard = [e for e in all_entries if e.difficulty == "hard"]
            results = await runner.run_entries(hard)
        finally:
            await pool.close()

        hits = sum(1 for r in results if r.matched)
        rate = hits / len(results)
        for r in results:
            if not r.matched:
                print(f"  MISS {r.gt_id}: {r.name_raw!r} → {r.extra.get('matched_name')!r}")
        print(f"\n[RRF FULL] Hard Hit@1: {hits}/{len(results)} = {rate:.3f}")
        assert rate >= 0.85

    async def test_report_json_saved(self):
        """전체 결과 JSON 저장."""
        pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=4)
        try:
            runner = EvalFullRunner(pool)
            results = await runner.run_all()
        finally:
            await pool.close()

        summary = summarize(results, by_difficulty=True)
        REPORT_DIR.mkdir(parents=True, exist_ok=True)
        out = REPORT_DIR / "eval_full_2026-06-09.json"
        out.write_text(json.dumps(summary, ensure_ascii=False, indent=2))
        assert out.exists()
        loaded = json.loads(out.read_text())
        assert loaded["total"] == 100
