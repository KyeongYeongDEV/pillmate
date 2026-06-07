"""
OCR/RAG 평가 지표 모듈

측정 지표:
  - Hit Rate@k    : top-k 안에 정답 KD code 포함 여부
  - MRR           : Mean Reciprocal Rank
  - Context Recall: retriever 가 정답을 포함한 비율
  - Faithfulness  : LLM-as-Judge (오프라인 모드에서는 stage 기반 추정)

출처: 식품의약품안전처 기반 GT 데이터
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


_STAGE_FAITHFULNESS: dict[str, float] = {
    "ilike": 0.98,
    "exact_fast": 0.97,
    "token": 0.94,
    "fuzzy": 0.88,
    "ingredient": 0.85,
    "rrf": 0.82,
    "vector": 0.78,
    "none": 0.0,
}


@dataclass
class EvalResult:
    gt_id: str
    gt_kd_code: str
    name_raw: str
    retrieved_kd_codes: list[str]
    stage: str
    matched: bool
    rank: int | None
    difficulty: str = "easy"
    extra: dict[str, Any] = field(default_factory=dict)


def hit_rate_at_k(results: list[EvalResult], k: int) -> float:
    if not results:
        return 0.0
    hits = sum(
        1 for r in results
        if r.rank is not None and r.rank <= k
    )
    return hits / len(results)


def mrr(results: list[EvalResult]) -> float:
    if not results:
        return 0.0
    total = sum(
        1.0 / r.rank for r in results
        if r.rank is not None
    )
    return total / len(results)


def context_recall(results: list[EvalResult]) -> float:
    if not results:
        return 0.0
    recalled = sum(1 for r in results if r.matched)
    return recalled / len(results)


def faithfulness_score(
    results: list[EvalResult],
    use_llm: bool = False,
    estimate_from_stage: bool = False,
) -> float | None:
    if use_llm:
        raise NotImplementedError(
            "LLM-as-Judge faithfulness requires Gemini Flash integration. "
            "Run with pytest -m eval --llm to enable."
        )
    if not estimate_from_stage:
        return None
    if not results:
        return 0.0
    total = sum(
        _STAGE_FAITHFULNESS.get(r.stage, 0.5) for r in results if r.matched
    )
    return total / len(results)


def summarize(
    results: list[EvalResult],
    by_difficulty: bool = False,
) -> dict[str, Any]:
    summary: dict[str, Any] = {
        "total": len(results),
        "hit_rate_at_1": hit_rate_at_k(results, k=1),
        "hit_rate_at_5": hit_rate_at_k(results, k=5),
        "hit_rate_at_10": hit_rate_at_k(results, k=10),
        "mrr": mrr(results),
        "context_recall": context_recall(results),
        "faithfulness_estimate": faithfulness_score(
            results, use_llm=False, estimate_from_stage=True
        ),
    }

    stage_counts: dict[str, int] = {}
    for r in results:
        stage_counts[r.stage] = stage_counts.get(r.stage, 0) + 1
    summary["stage_distribution"] = stage_counts

    if by_difficulty:
        groups: dict[str, list[EvalResult]] = {}
        for r in results:
            groups.setdefault(r.difficulty, []).append(r)
        summary["by_difficulty"] = {
            diff: {
                "total": len(group),
                "hit_rate_at_1": hit_rate_at_k(group, k=1),
                "mrr": mrr(group),
                "context_recall": context_recall(group),
            }
            for diff, group in groups.items()
        }

    return summary
