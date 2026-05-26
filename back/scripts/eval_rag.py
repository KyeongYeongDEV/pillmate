"""
PillMate RAG 평가 스크립트 (RAGAS 기반)

사용:
    python scripts/eval_rag.py \
        --dataset eval/medical-qa-100.jsonl \
        --output reports/rag-eval-2026-05-21.json

지표:
    - Faithfulness: 응답이 검색 청크에 근거하는가
    - Answer Relevancy: 응답이 질문에 답하는가
    - Context Precision: 검색 청크의 정밀도
    - Context Recall: 정답이 검색에 포함된 비율
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load_dataset(path: Path) -> list[dict]:
    """JSONL 데이터셋 로드"""
    with path.open("r", encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def evaluate(dataset: list[dict]) -> dict:
    """RAGAS 평가 실행 (구현은 ai_server 모듈에서)"""
    # TODO: ai_server.app.eval.ragas_eval 호출
    raise NotImplementedError(
        "ai_server/app/eval/ragas_eval.py 구현 필요. "
        "skills/rag-eval.md 참조."
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="PillMate RAG 평가")
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--fail-below",
        type=float,
        default=0.95,
        help="Faithfulness가 이 값 미만이면 종료 코드 1",
    )
    args = parser.parse_args()

    if not args.dataset.exists():
        print(f"❌ 데이터셋 없음: {args.dataset}", file=sys.stderr)
        return 1

    dataset = load_dataset(args.dataset)
    print(f"📊 평가 시작 ({len(dataset)} 샘플)")

    try:
        results = evaluate(dataset)
    except NotImplementedError as e:
        print(f"⚠️  {e}", file=sys.stderr)
        return 2

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2))
    print(f"✅ 결과 저장: {args.output}")

    faithfulness = results.get("faithfulness", 0.0)
    if faithfulness < args.fail_below:
        print(
            f"❌ Faithfulness {faithfulness:.3f} < {args.fail_below}",
            file=sys.stderr,
        )
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
