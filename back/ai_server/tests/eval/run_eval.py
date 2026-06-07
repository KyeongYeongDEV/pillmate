"""
OCR/RAG 평가 실행 스크립트

사용:
    python tests/eval/run_eval.py                          # 오프라인 평가
    python tests/eval/run_eval.py --out reports/eval/baseline_2026-06-07.json

결과:
    reports/eval/{date}.json  — 시계열 저장
"""
from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from tests.eval.conftest import GT_JSONL
from tests.eval.metrics import EvalResult, summarize
from tests.eval.test_ocr_eval import _build_eval_results

REPORT_DIR = Path(__file__).parent.parent.parent / "reports" / "eval"


def _load_gt() -> list[dict]:
    return [json.loads(line) for line in GT_JSONL.read_text().splitlines() if line.strip()]


def run(out_path: Path | None = None) -> dict:
    gt_entries = _load_gt()
    results: list[EvalResult] = _build_eval_results(gt_entries)
    summary = summarize(results, by_difficulty=True)
    summary["evaluated_at"] = datetime.now(timezone.utc).isoformat()
    summary["gt_path"] = str(GT_JSONL)
    summary["mode"] = "offline"

    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    if out_path is None:
        date_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        out_path = REPORT_DIR / f"{date_str}.json"

    out_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    return summary


def _print_summary(summary: dict) -> None:
    print(f"\n{'=' * 55}")
    print("  PillMate OCR/RAG 평가 결과 (오프라인 baseline)")
    print(f"{'=' * 55}")
    print(f"  총 케이스     : {summary['total']:>5}")
    print(f"  Hit Rate @1   : {summary['hit_rate_at_1']:.3f}")
    print(f"  Hit Rate @5   : {summary['hit_rate_at_5']:.3f}")
    print(f"  Hit Rate @10  : {summary['hit_rate_at_10']:.3f}")
    print(f"  MRR           : {summary['mrr']:.3f}")
    print(f"  Context Recall: {summary['context_recall']:.3f}")
    print(f"  Faithfulness* : {summary.get('faithfulness_estimate') or 'N/A (LLM 필요)'}")
    print(f"\n  * stage 기반 추정값 (실제 LLM-judge 비용 < $0.10)")
    print(f"\n  난이도별 결과:")
    for diff, stats in summary.get("by_difficulty", {}).items():
        print(f"    {diff:8s}: total={stats['total']:3d}  hit@1={stats['hit_rate_at_1']:.2f}  mrr={stats['mrr']:.2f}")
    print(f"\n  Stage 분포:")
    for stage, count in sorted(summary.get("stage_distribution", {}).items(), key=lambda x: -x[1]):
        print(f"    {stage:12s}: {count:3d}건")
    print(f"{'=' * 55}\n")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()
    summary = run(args.out)
    _print_summary(summary)
    print(f"📄 결과 저장: {args.out or REPORT_DIR}")
