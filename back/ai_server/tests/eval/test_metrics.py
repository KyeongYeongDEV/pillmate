"""metrics.py 단위 테스트 — TDD RED → GREEN"""
from __future__ import annotations

import pytest

from tests.eval.metrics import (
    EvalResult,
    context_recall,
    faithfulness_score,
    hit_rate_at_k,
    mrr,
    summarize,
)


def _result(gt_kd: str, retrieved: list[str], stage: str = "ilike") -> EvalResult:
    return EvalResult(
        gt_id="gt_xxx",
        gt_kd_code=gt_kd,
        name_raw="테스트약정",
        retrieved_kd_codes=retrieved,
        stage=stage,
        matched=gt_kd in retrieved,
        rank=retrieved.index(gt_kd) + 1 if gt_kd in retrieved else None,
    )


class TestHitRateAtK:
    def test_perfect_score_all_at_1(self):
        results = [_result("KD001", ["KD001"]) for _ in range(5)]
        assert hit_rate_at_k(results, k=1) == 1.0

    def test_zero_when_none_match(self):
        results = [_result("KD001", ["KD999"]) for _ in range(5)]
        assert hit_rate_at_k(results, k=1) == 0.0

    def test_hit_rate_at_5_includes_lower_ranks(self):
        results = [
            _result("KD001", ["KD999", "KD998", "KD997", "KD996", "KD001"]),
            _result("KD001", ["KD999"]),
        ]
        assert hit_rate_at_k(results, k=5) == 0.5

    def test_hit_at_k_ignores_beyond_k(self):
        results = [_result("KD001", ["KD999", "KD998", "KD997", "KD996", "KD001"])]
        assert hit_rate_at_k(results, k=4) == 0.0
        assert hit_rate_at_k(results, k=5) == 1.0

    def test_empty_results_returns_zero(self):
        assert hit_rate_at_k([], k=1) == 0.0


class TestMRR:
    def test_rank_1_gives_full_score(self):
        results = [_result("KD001", ["KD001"])]
        assert mrr(results) == pytest.approx(1.0)

    def test_rank_2_gives_half(self):
        results = [_result("KD001", ["KD999", "KD001"])]
        assert mrr(results) == pytest.approx(0.5)

    def test_no_match_contributes_zero(self):
        results = [_result("KD001", ["KD999"])]
        assert mrr(results) == pytest.approx(0.0)

    def test_mrr_averages_across_queries(self):
        results = [
            _result("KD001", ["KD001"]),
            _result("KD002", ["KD999", "KD002"]),
        ]
        assert mrr(results) == pytest.approx(0.75)

    def test_empty_results_returns_zero(self):
        assert mrr([]) == 0.0


class TestContextRecall:
    def test_all_retrieved_gives_1(self):
        results = [_result("KD001", ["KD001"]) for _ in range(4)]
        assert context_recall(results) == 1.0

    def test_none_retrieved_gives_0(self):
        results = [_result("KD001", ["KD999"]) for _ in range(4)]
        assert context_recall(results) == 0.0

    def test_partial_recall(self):
        results = [
            _result("KD001", ["KD001"]),
            _result("KD002", ["KD999"]),
            _result("KD003", ["KD003"]),
            _result("KD004", ["KD999"]),
        ]
        assert context_recall(results) == pytest.approx(0.5)


class TestFaithfulnessScore:
    def test_offline_placeholder_returns_none(self):
        results = [_result("KD001", ["KD001"])]
        score = faithfulness_score(results, use_llm=False)
        assert score is None

    def test_all_matched_auto_estimate(self):
        results = [_result("KD001", ["KD001"], stage="ilike") for _ in range(5)]
        score = faithfulness_score(results, use_llm=False, estimate_from_stage=True)
        assert score is not None
        assert 0.0 <= score <= 1.0

    def test_estimate_increases_with_higher_confidence_stages(self):
        exact_results = [_result("KD001", ["KD001"], stage="ilike") for _ in range(5)]
        vector_results = [_result("KD001", ["KD001"], stage="vector") for _ in range(5)]
        exact_score = faithfulness_score(exact_results, use_llm=False, estimate_from_stage=True)
        vector_score = faithfulness_score(vector_results, use_llm=False, estimate_from_stage=True)
        assert exact_score is not None
        assert vector_score is not None
        assert exact_score >= vector_score


class TestSummarize:
    def test_summarize_returns_all_metrics(self):
        results = [
            _result("KD001", ["KD001"]),
            _result("KD002", ["KD999", "KD002"]),
            _result("KD003", ["KD999"]),
        ]
        summary = summarize(results)
        assert "hit_rate_at_1" in summary
        assert "hit_rate_at_5" in summary
        assert "hit_rate_at_10" in summary
        assert "mrr" in summary
        assert "context_recall" in summary
        assert "total" in summary
        assert summary["total"] == 3

    def test_summarize_by_difficulty(self):
        easy = [_result("KD001", ["KD001"]) for _ in range(3)]
        hard = [_result("KD002", ["KD999"]) for _ in range(2)]
        for r in easy:
            object.__setattr__(r, "difficulty", "easy")
        for r in hard:
            object.__setattr__(r, "difficulty", "hard")
        all_results = easy + hard
        summary = summarize(all_results, by_difficulty=True)
        assert "by_difficulty" in summary
