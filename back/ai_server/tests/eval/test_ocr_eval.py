"""
OCR/RAG 매칭 파이프라인 오프라인 평가 테스트 — pytest -m eval

평가 전략:
  - Vision 레이어(Gemini) 는 Mock — name_raw 를 GT 에서 직접 주입
  - DrugMatcher/RrfMatcher 는 오프라인 모드 시뮬레이션
    - ILIKE hit 여부: GT name_raw 가 GT 정답 name 에 포함되는지
    - 토큰 hit: 첫 토큰이 정답 name 에 포함되는지
    - Fuzzy hit: Levenshtein distance <= 2
    - Vector/Ingredient: 오프라인 — None (미지원)
  - 실제 DB 연결 없이 matching logic 의 오프라인 성능 측정

참고: 실제 DB + Gemini API 연결 시는 pytest -m integration --llm 으로 실행
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from tests.eval.metrics import EvalResult, summarize

REPORT_DIR = Path(__file__).parent.parent.parent / "reports" / "eval"

try:
    from Levenshtein import distance as levenshtein_distance
    HAS_LEVENSHTEIN = True
except ImportError:
    HAS_LEVENSHTEIN = False


def _ilike_match(name_raw: str, gt_name: str) -> bool:
    return name_raw.lower() in gt_name.lower() or gt_name.lower() in name_raw.lower()


def _token_match(name_raw: str, gt_name: str) -> bool:
    first_token = name_raw.strip().split()[0] if " " in name_raw else name_raw[:3]
    return first_token in gt_name


def _fuzzy_match(name_raw: str, gt_name: str) -> bool:
    if not HAS_LEVENSHTEIN:
        return False
    short = name_raw[:len(gt_name)]
    return levenshtein_distance(short, gt_name[:len(short)]) <= 2


def _simulate_offline_match(name_raw: str, gt_entry: dict) -> tuple[bool, str, int | None]:
    """오프라인 매칭 시뮬레이션 — 실제 DB 없이 stage 추정."""
    gt_name = gt_entry["drugs"][0]["name"]
    gt_kd = gt_entry["drugs"][0]["kd_code"]

    if _ilike_match(name_raw, gt_name):
        return True, "ilike", 1

    if _token_match(name_raw, gt_name):
        return True, "token", 1

    if _fuzzy_match(name_raw, gt_name):
        return True, "fuzzy", 1

    stage_hint = gt_entry["metadata"].get("stage_hint", "none")
    if stage_hint in ("ingredient", "vector"):
        return False, stage_hint, None

    return False, "none", None


def _build_eval_results(gt_entries: list[dict]) -> list[EvalResult]:
    results = []
    for entry in gt_entries:
        name_raw = entry["name_raw"]
        gt_kd = entry["drugs"][0]["kd_code"]
        matched, stage, rank = _simulate_offline_match(name_raw, entry)
        retrieved = [gt_kd] if matched else ["KD_MISS"]
        results.append(
            EvalResult(
                gt_id=entry["id"],
                gt_kd_code=gt_kd,
                name_raw=name_raw,
                retrieved_kd_codes=retrieved,
                stage=stage,
                matched=matched,
                rank=rank,
                difficulty=entry.get("difficulty", "easy"),
                extra={
                    "stage_hint": entry["metadata"].get("stage_hint"),
                    "type": entry["metadata"].get("type"),
                },
            )
        )
    return results


@pytest.mark.eval
class TestOfflineOcrEval:
    def test_gt_dataset_loaded(self, gt_entries):
        assert len(gt_entries) == 100

    def test_gt_has_required_fields(self, gt_entries):
        for entry in gt_entries:
            assert "id" in entry
            assert "name_raw" in entry
            assert "drugs" in entry
            assert len(entry["drugs"]) >= 1
            assert "kd_code" in entry["drugs"][0]
            assert "metadata" in entry
            assert entry["metadata"].get("source") == "식품의약품안전처"

    def test_easy_cases_hit_rate_at_1(self, gt_by_difficulty):
        easy = gt_by_difficulty.get("easy", [])
        assert len(easy) > 0
        results = _build_eval_results(easy)
        rate = sum(1 for r in results if r.matched) / len(results)
        assert rate >= 0.6, f"easy hit rate too low: {rate:.2f}"

    def test_full_dataset_hit_rate_at_1_above_threshold(self, gt_entries):
        results = _build_eval_results(gt_entries)
        from tests.eval.metrics import hit_rate_at_k
        rate = hit_rate_at_k(results, k=1)
        assert rate >= 0.4, f"overall hit_rate@1 too low: {rate:.2f} (expected >= 0.40)"

    def test_summarize_generates_all_metrics(self, gt_entries):
        results = _build_eval_results(gt_entries)
        summary = summarize(results, by_difficulty=True)
        assert summary["total"] == 100
        assert "hit_rate_at_1" in summary
        assert "hit_rate_at_5" in summary
        assert "mrr" in summary
        assert "context_recall" in summary
        assert "by_difficulty" in summary

    def test_report_json_saved(self, gt_entries, tmp_path):
        results = _build_eval_results(gt_entries)
        summary = summarize(results, by_difficulty=True)
        out = tmp_path / "eval_test.json"
        out.write_text(json.dumps(summary, ensure_ascii=False, indent=2))
        loaded = json.loads(out.read_text())
        assert loaded["total"] == 100
