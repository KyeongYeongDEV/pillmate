"""
#150 T-AI-WIRE-RRFMATCHER-PROD — Gate 2: 단독 RrfMatcher 수치 재현

main.py 와이어링 없이 RrfMatcher 전체 파이프라인을 GT100 에 직접 돌려
Hit@1 측정. run_eval_full.py 의 0.97 baseline 과 나란히 비교.

파이프라인:
  parse_drug_item(name_raw)
  → RrfMatcher {
      exact_fast: ExactIlikeAdapter
      rrf: IlikeMultiAdapter + TrigramMultiAdapter
      reranker: DomainReranker → BgeRerankerAdapter (normalize=True)
      decider: MatchDecider(ABS_THRESHOLD=0.70)
    }
  → MatchResult.decision.primary.item_seq == gt_kd_code → Hit/Miss

실행: cd back/ai_server && .venv/bin/python tests/eval/run_gate2_rrf_eval.py
      (VectorMultiAdapter 는 OpenAI_API_KEY 가 있을 때 자동 포함)

read-only: DB SELECT 만, DELETE/UPDATE 없음.
"""
from __future__ import annotations

import asyncio
import json
import os
import sys
from dataclasses import dataclass, field
from pathlib import Path

import asyncpg

# ── 프로젝트 루트를 sys.path 에 추가 ──────────────────────────────
_HERE = Path(__file__).parent
_PROJECT_ROOT = _HERE.parent.parent
if str(_PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT))

# ── .env 로드 (pydantic-settings 없이 간단히) ─────────────────────
_ENV_PATH = _PROJECT_ROOT.parent / ".env"
if _ENV_PATH.exists():
    for _line in _ENV_PATH.read_text().splitlines():
        _line = _line.strip()
        if _line and not _line.startswith("#") and "=" in _line:
            _k, _, _v = _line.partition("=")
            os.environ.setdefault(_k.strip(), _v.strip())

from app.rag.ocr.decider import MatchDecider
from app.rag.ocr.fuzzy_search import JamoFuzzyRanker, TrigramFuzzySearch
from app.rag.ocr.parser import ParsedItem, parse_drug_item
from app.rag.ocr.reranker import BgeRerankerAdapter, DomainReranker
from app.rag.ocr.rrf import MatchDecisionType
from app.rag.ocr.rrf_adapters import (
    ExactIlikeAdapter,
    IlikeMultiAdapter,
    IngredientMultiAdapter,
    PrefixRelaxMultiAdapter,
    TokenIlikeMultiAdapter,
    TrigramMultiAdapter,
)
from app.rag.ocr.rrf_matcher import RrfMatcher

GT_JSONL = _HERE / "gt" / "prescriptions.jsonl"
REPORT_DIR = _PROJECT_ROOT / "reports" / "eval"
POSTGRES_DSN = os.getenv(
    "POSTGRES_DSN",
    "postgresql://pillmate:pillmate_local@localhost:5433/pillmate",
)

BASELINE_HIT_AT_1 = 0.97  # run_eval_full.py (legacy cascade) 결과

# ── INN 키워드 매칭 (eval_full 동일 로직) ─────────────────────────
_HARD_INN: dict[str, str] = {
    "gt_031": "세티리진", "gt_032": "암로디핀", "gt_033": "오메프라졸",
    "gt_034": "메트포르민", "gt_035": "로수바스타틴", "gt_071": "타이레놀",
    "gt_072": "아목시실린", "gt_073": "이부프로펜", "gt_074": "아스피린",
    "gt_075": "메트포르민", "gt_076": "암로디핀", "gt_077": "로수바스타틴",
    "gt_078": "오메프라졸", "gt_079": "세티리진", "gt_080": "클래리스로마이신",
    "gt_086": "가바펜틴", "gt_087": "졸피뎀", "gt_088": "로페라미드",
    "gt_089": "글리메피리", "gt_090": "모사프리드", "gt_092": "에소메프라졸",
    "gt_096": "이부프로펜", "gt_097": "졸피뎀", "gt_098": "아목시실린",
    "gt_099": "메트포르민", "gt_100": "아목시실린",
}
_MEDIUM_INN: dict[str, str] = {
    "gt_016": "리오노필", "gt_017": "타이레놀", "gt_018": "아목시",
    "gt_019": "메트포르민", "gt_020": "암로디핀", "gt_021": "타이레놀",
    "gt_022": "아목시실린", "gt_023": "이부프로", "gt_024": "아스피린",
    "gt_025": "메트포르민", "gt_036": "타이레놀", "gt_037": "아목시",
    "gt_038": "이부프로", "gt_039": "아스피린", "gt_040": "메트포르민",
    "gt_051": "타이레놀", "gt_052": "아목시실린", "gt_053": "이부프로펜",
    "gt_054": "아스피린", "gt_055": "메트포르민", "gt_056": "암로디핀",
    "gt_057": "로수바스타틴", "gt_058": "오메프라졸", "gt_059": "세티리진",
    "gt_060": "클래리스로마이신", "gt_061": "독시사이클린", "gt_062": "프레드니솔론",
    "gt_063": "레보플록사신", "gt_064": "에소메프라졸", "gt_065": "세파드록실",
    "gt_066": "가바펜틴", "gt_067": "졸피뎀", "gt_068": "로페라미드",
    "gt_069": "글리메피", "gt_070": "모사프리드", "gt_081": "타이레놀",
    "gt_082": "아목시실린", "gt_083": "이부프로", "gt_084": "메트포르민",
    "gt_085": "오메프라졸", "gt_091": "레보플록사신", "gt_093": "로수바스타틴",
    "gt_094": "세파클", "gt_095": "글리메피",
}


def _auto_inn(gt_drug_name: str) -> str:
    """easy GT 약품명에서 INN 추출 — 첫 한국어 연속 4자 (run_eval_full.py 동일 로직)."""
    import re
    m = re.match(r"^([가-힣]{2,})", gt_drug_name)
    if m:
        return m.group(1)[:4]
    return gt_drug_name[:4]


def _get_inn(gt_id: str, gt_drug_name: str) -> str:
    """GT ID 로 INN 조회 — hard → medium → auto(easy) 순."""
    if gt_id in _HARD_INN:
        return _HARD_INN[gt_id]
    if gt_id in _MEDIUM_INN:
        return _MEDIUM_INN[gt_id]
    return _auto_inn(gt_drug_name)


def _check_hit(gt_id: str, gt_kd_code: str, gt_drug_name: str, predicted_kd_code: str | None, drug_name: str | None) -> bool:
    """run_eval_full.py 동일 hit 판정: INN 키워드 포함 여부."""
    if predicted_kd_code is not None and predicted_kd_code == gt_kd_code:
        return True
    inn = _get_inn(gt_id, gt_drug_name)
    return bool(inn and drug_name and inn in drug_name)


@dataclass
class EvalRow:
    gt_id: str
    name_raw: str
    gt_kd_code: str
    gt_drug_name: str
    difficulty: str
    predicted_kd_code: str | None
    predicted_name: str | None
    top1_option_name: str | None  # options[0].name even if MANUAL
    decision_type: str
    final_score: float
    hit: bool          # decision-level hit (AUTO/CONFIRM primary match)
    retrieval_hit: bool  # retrieval-level hit (top1 option contains INN)


async def _build_matcher(pool) -> RrfMatcher:
    """RrfMatcher 풀 파이프라인 조립."""
    exact = ExactIlikeAdapter(pool=pool)
    ilike_multi = IlikeMultiAdapter(pool=pool)
    trgm_search = TrigramFuzzySearch(pool=pool)
    jamo_ranker = JamoFuzzyRanker()
    trgm_multi = TrigramMultiAdapter(trgm_search=trgm_search, ranker=jamo_ranker)

    # Gate A: legacy cascade fallback adapters
    token_ilike = TokenIlikeMultiAdapter(pool=pool)
    prefix_relax = PrefixRelaxMultiAdapter(pool=pool)
    ingredient = IngredientMultiAdapter(pool=pool)

    retrievers = {
        "ilike": ilike_multi,
        "trigram": trgm_multi,
        "token_ilike": token_ilike,
        "prefix_relax": prefix_relax,
        "ingredient": ingredient,
    }

    # VectorMultiAdapter — OpenAI key 있고 openai 패키지 설치된 경우만 포함
    openai_key = os.environ.get("OpenAI_API_KEY") or os.environ.get("OPENAI_API_KEY")
    if openai_key:
        try:
            from app.rag.ocr.rrf_adapters import VectorMultiAdapter
            from app.rag.pgvector_retriever import OpenAIEmbeddingAdapter, PgVectorRetriever
            embedder = OpenAIEmbeddingAdapter(api_key=openai_key, model="text-embedding-3-small", dimensions=768)
            vector_retriever = PgVectorRetriever(pool=pool, embedder=embedder)
            retrievers["vector"] = VectorMultiAdapter(retriever=vector_retriever, top_k=10)
            print(f"[INFO] VectorMultiAdapter 포함 (OpenAI key 확인됨)")
        except (ImportError, ModuleNotFoundError) as e:
            print(f"[WARN] VectorMultiAdapter 미포함 (openai 패키지 없음: {e}) — ILIKE+Trigram 2-retriever 모드")
    else:
        print(f"[WARN] VectorMultiAdapter 미포함 (OpenAI_API_KEY 없음) — ILIKE+Trigram 2-retriever 모드")

    bge = BgeRerankerAdapter()
    reranker = DomainReranker()
    decider = MatchDecider()

    return RrfMatcher(
        exact_single=exact,
        retrievers=retrievers,
        reranker=reranker,
        bge_reranker=bge,
        decider=decider,
    )


async def run_eval(pool) -> list[EvalRow]:
    matcher = await _build_matcher(pool)

    gt_items = []
    with open(GT_JSONL) as f:
        for line in f:
            gt_items.append(json.loads(line.strip()))

    print(f"[INFO] GT 항목 수: {len(gt_items)}")
    rows: list[EvalRow] = []

    for i, item in enumerate(gt_items):
        gt_id = item["id"]
        name_raw = item["name_raw"]
        gt_kd_code = item["drugs"][0]["kd_code"]
        difficulty = item.get("difficulty", "unknown")

        parsed = parse_drug_item(name_raw)
        try:
            result = await matcher.match(parsed)
        except Exception as exc:
            print(f"[ERROR] {gt_id} ({name_raw}): {exc}")
            rows.append(EvalRow(
                gt_id=gt_id, name_raw=name_raw, gt_kd_code=gt_kd_code,
                difficulty=difficulty, predicted_kd_code=None, predicted_name=None,
                decision_type="ERROR", final_score=0.0, hit=False,
            ))
            continue

        decision = result.decision
        if decision is not None and decision.primary is not None:
            predicted_kd = decision.primary.item_seq
            predicted_name = decision.primary.name
            decision_type = decision.type.value
        else:
            predicted_kd = None
            predicted_name = None
            decision_type = "MANUAL"

        final_score = result.final_score or 0.0
        gt_drug_name = item["drugs"][0]["name"]
        hit = _check_hit(gt_id, gt_kd_code, gt_drug_name, predicted_kd, predicted_name)

        # retrieval-level hit: top-1 candidate in options (even if MANUAL)
        top1_option_name: str | None = None
        if decision is not None and decision.options:
            top1_option_name = decision.options[0].name
        retrieval_hit = _check_hit(gt_id, gt_kd_code, gt_drug_name, None, top1_option_name)

        rows.append(EvalRow(
            gt_id=gt_id, name_raw=name_raw, gt_kd_code=gt_kd_code,
            gt_drug_name=gt_drug_name, difficulty=difficulty, predicted_kd_code=predicted_kd,
            predicted_name=predicted_name, top1_option_name=top1_option_name,
            decision_type=decision_type, final_score=final_score, hit=hit,
            retrieval_hit=retrieval_hit,
        ))

        status = "HIT" if hit else "MISS"
        print(f"  [{i+1:3d}/{len(gt_items)}] {gt_id} {status:4s} {decision_type:7s} score={final_score:.3f}  {name_raw[:30]}")

    return rows


def _compute_report(rows: list[EvalRow]) -> dict:
    total = len(rows)
    hits = sum(1 for r in rows if r.hit)

    by_diff: dict[str, dict] = {}
    for r in rows:
        d = r.difficulty
        if d not in by_diff:
            by_diff[d] = {"total": 0, "hits": 0}
        by_diff[d]["total"] += 1
        by_diff[d]["hits"] += (1 if r.hit else 0)

    decision_dist: dict[str, dict] = {"AUTO": {"total": 0, "hits": 0}, "CONFIRM": {"total": 0, "hits": 0}, "MANUAL": {"total": 0, "hits": 0}}
    for r in rows:
        dt = r.decision_type if r.decision_type in decision_dist else "MANUAL"
        decision_dist[dt]["total"] += 1
        decision_dist[dt]["hits"] += (1 if r.hit else 0)

    score_buckets: dict[str, int] = {"0.0-0.5": 0, "0.5-0.6": 0, "0.6-0.7": 0, "0.7-0.8": 0, "0.8-0.9": 0, "0.9-1.0": 0}
    for r in rows:
        s = r.final_score
        if s < 0.5:
            score_buckets["0.0-0.5"] += 1
        elif s < 0.6:
            score_buckets["0.5-0.6"] += 1
        elif s < 0.7:
            score_buckets["0.6-0.7"] += 1
        elif s < 0.8:
            score_buckets["0.7-0.8"] += 1
        elif s < 0.9:
            score_buckets["0.8-0.9"] += 1
        else:
            score_buckets["0.9-1.0"] += 1

    misses = [r for r in rows if not r.hit]

    retrieval_hits = sum(1 for r in rows if r.retrieval_hit)

    return {
        "total": total,
        "hits": hits,
        "hit_at_1": hits / total,
        "retrieval_hits": retrieval_hits,
        "retrieval_hit_at_1": retrieval_hits / total,
        "by_difficulty": by_diff,
        "decision_dist": decision_dist,
        "score_buckets": score_buckets,
        "misses": misses,
    }


def _format_report(rep: dict, retrievers_used: list[str]) -> str:
    hit_at_1 = rep["hit_at_1"]
    total = rep["total"]
    hits = rep["hits"]
    baseline = BASELINE_HIT_AT_1

    retrieval_hit_at_1 = rep["retrieval_hit_at_1"]
    retrieval_hits = rep["retrieval_hits"]

    lines = [
        "# Gate 2 — RrfMatcher 단독 GT100 평가",
        "",
        f"> 날짜: 2026-06-13  ",
        f"> 목적: main.py 와이어링 없이 RrfMatcher 파이프라인 수치 재현  ",
        f"> Retriever: {', '.join(retrievers_used)}  ",
        f"> Reranker: DomainReranker → BgeRerankerAdapter(normalize=True)  ",
        f"> ABS_THRESHOLD=0.70  ",
        "",
        "## 1. 핵심 비교",
        "",
        "| 경로 | Hit@1 | 건수 | 비고 |",
        "|------|-------|------|------|",
        f"| legacy cascade (run_eval_full.py) | {baseline:.1%} | 97/100 | ilike→token→fuzzy→ingredient→prefix |",
        f"| **RrfMatcher — 결정 Hit@1** | **{hit_at_1:.1%}** | **{hits}/{total}** | AUTO/CONFIRM primary 기준 |",
        f"| **RrfMatcher — 검색 Hit@1** | **{retrieval_hit_at_1:.1%}** | **{retrieval_hits}/{total}** | MANUAL 포함 options[0] 기준 |",
        "",
        "> 검색 Hit@1 = 임계값(0.70) 없이 top-1 후보가 정답인 비율.",
        "> 결정 Hit@1 = 임계값 통과 후 AUTO/CONFIRM으로 확정된 비율.",
        "",
    ]

    # difficulty breakdown
    lines += ["## 2. Difficulty별 분해", "", "| Difficulty | 건수 | Hit | Hit@1 |", "|-----------|------|-----|-------|"]
    for diff in ["easy", "medium", "hard"]:
        d = rep["by_difficulty"].get(diff, {"total": 0, "hits": 0})
        rate = d["hits"] / d["total"] if d["total"] > 0 else 0.0
        lines.append(f"| {diff} | {d['total']} | {d['hits']} | {rate:.1%} |")
    lines.append("")

    # decision distribution
    lines += ["## 3. Decision type 분포 (ABS_THRESHOLD=0.70)", "", "| Type | 건수 | Hit | 정답률 |", "|------|------|-----|--------|"]
    for dt in ["AUTO", "CONFIRM", "MANUAL"]:
        d = rep["decision_dist"][dt]
        rate = d["hits"] / d["total"] if d["total"] > 0 else 0.0
        lines.append(f"| {dt} | {d['total']} | {d['hits']} | {rate:.1%} |")
    lines.append("")

    # score histogram
    lines += ["## 4. final_score 분포 (BGE 정규화 후 0~1)", "", "| Score 범위 | 건수 |", "|-----------|------|"]
    for bucket, cnt in rep["score_buckets"].items():
        lines.append(f"| {bucket} | {cnt} |")
    lines.append("")

    # miss list
    misses = rep["misses"]
    lines += [f"## 5. Miss 목록 ({len(misses)}건)", ""]
    if misses:
        lines.append("| GT ID | Difficulty | name_raw | Predicted | Score | Decision |")
        lines.append("|-------|-----------|----------|-----------|-------|----------|")
        for r in sorted(misses, key=lambda x: x.difficulty):
            pred = r.predicted_name or "(none)"
            lines.append(f"| {r.gt_id} | {r.difficulty} | {r.name_raw} | {pred[:25]} | {r.final_score:.3f} | {r.decision_type} |")
    lines.append("")

    # CTO 판단 항목
    diff_pct = hit_at_1 - baseline
    lines += ["## 6. CTO 판단 필요 항목", ""]
    if abs(diff_pct) > 0.02:
        direction = "향상" if diff_pct > 0 else "하락"
        lines.append(f"- RrfMatcher Hit@1 {hit_at_1:.1%} vs baseline {baseline:.1%} → **{direction} {abs(diff_pct):.1%}**")
        lines.append(f"- Hit@1 {'개선되었으나 오차 범위 검토 필요' if diff_pct > 0 else '하락 — 운영 와이어링 전 원인 파악 필요'}")
    else:
        lines.append(f"- RrfMatcher Hit@1 {hit_at_1:.1%} ≈ baseline {baseline:.1%} (±2% 이내) → 통계적으로 동등")
    lines.append("")
    lines.append("**AUTO 자동확정 정답률**: " + str(
        f"{rep['decision_dist']['AUTO']['hits']}/{rep['decision_dist']['AUTO']['total']} = "
        f"{rep['decision_dist']['AUTO']['hits']/rep['decision_dist']['AUTO']['total']:.1%}"
        if rep['decision_dist']['AUTO']['total'] > 0 else "0건"
    ))
    lines.append("")

    return "\n".join(lines)


async def main():
    print("=" * 60)
    print("Gate 2 — RrfMatcher GT100 단독 평가 (main.py 무수정)")
    print("=" * 60)

    pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=2, max_size=5)
    try:
        rows = await run_eval(pool)
    finally:
        await pool.close()

    rep = _compute_report(rows)

    openai_key = os.environ.get("OpenAI_API_KEY") or os.environ.get("OPENAI_API_KEY")
    retrievers_used = [
        "ExactIlikeAdapter", "IlikeMultiAdapter", "TrigramMultiAdapter",
        "TokenIlikeMultiAdapter", "PrefixRelaxMultiAdapter", "IngredientMultiAdapter",
    ]
    try:
        import openai as _openai_test  # noqa: F401
        if openai_key:
            retrievers_used.append("VectorMultiAdapter")
    except ImportError:
        pass

    report_md = _format_report(rep, retrievers_used)

    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = REPORT_DIR / "gate2_rrf_path_2026-06-13.md"
    out_path.write_text(report_md, encoding="utf-8")
    print()
    print("=" * 60)
    print(report_md)
    print("=" * 60)
    print(f"\n[SAVED] {out_path}")

    # Gate 2 완료 요약
    print("\n" + "=" * 60)
    print("Gate 2 완료 — CTO 보고")
    print("=" * 60)
    print(f"  (1) RrfMatcher 결정 Hit@1 : {rep['hit_at_1']:.1%} ({rep['hits']}/{rep['total']})  ← AUTO/CONFIRM 확정만")
    print(f"  (1) RrfMatcher 검색 Hit@1 : {rep['retrieval_hit_at_1']:.1%} ({rep['retrieval_hits']}/{rep['total']})  ← 임계값 무시, options[0] 기준")
    print(f"  (1) Baseline (eval_full)  : {BASELINE_HIT_AT_1:.1%}")
    diff = rep["hit_at_1"] - BASELINE_HIT_AT_1
    print(f"  (1) 결정 vs baseline 차이 : {diff:+.1%}")
    print(f"  (2) Difficulty 분해 :")
    for diff_k in ["easy", "medium", "hard"]:
        d = rep["by_difficulty"].get(diff_k, {"total": 0, "hits": 0})
        rate = d["hits"] / d["total"] if d["total"] > 0 else 0.0
        print(f"      {diff_k:6s}: {d['hits']}/{d['total']} = {rate:.1%}")
    print(f"  (3) AUTO/CONFIRM/MANUAL 분포 :")
    for dt in ["AUTO", "CONFIRM", "MANUAL"]:
        d = rep["decision_dist"][dt]
        rate = d["hits"] / d["total"] if d["total"] > 0 else 0.0
        print(f"      {dt:7s}: {d['total']:3d}건  정답률 {rate:.1%}")
    print(f"  (4) 다음 단계 : CTO 판단 후 Gate 3 (main.py 와이어링) 진행 여부 결정")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
