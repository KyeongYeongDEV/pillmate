"""
#150 Gate A+ → Gate B2 — StrongExactAdapter 순수 랭킹 Hit@1 (2026-06-13)

Gate B (84%) 대비 StrongExactAdapter 도입 후 개선량 측정.
legacy cascade 97% 와 나란히 비교.

변경 사항:
  - ExactIlikeAdapter → StrongExactAdapter (dose_amount gate 제거 + salt strip + prefix relax)
  - RrfMatcher.match() dose_amount gate 제거됨
  - INN dict: gt_047 "졸피뎀타" → "졸피뎀" (주석산졸피뎀 형태 DB 반영)

실행: cd back/ai_server && .venv/bin/python tests/eval/run_gate_b2_ranking.py
read-only: DB SELECT만, DELETE 없음. main.py 무수정.
"""
from __future__ import annotations

import asyncio
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path

import asyncpg

_HERE = Path(__file__).parent
_PROJECT_ROOT = _HERE.parent.parent
if str(_PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT))

_ENV_PATH = _PROJECT_ROOT.parent / ".env"
if _ENV_PATH.exists():
    for _line in _ENV_PATH.read_text().splitlines():
        _line = _line.strip()
        if _line and not _line.startswith("#") and "=" in _line:
            _k, _, _v = _line.partition("=")
            os.environ.setdefault(_k.strip(), _v.strip())

from app.rag.ocr.decider import MatchDecider
from app.rag.ocr.fuzzy_search import JamoFuzzyRanker, TrigramFuzzySearch
from app.rag.ocr.parser import parse_drug_item
from app.rag.ocr.reranker import BgeRerankerAdapter, DomainReranker
from app.rag.ocr.rrf_adapters import (
    IlikeMultiAdapter,
    IngredientMultiAdapter,
    PrefixRelaxMultiAdapter,
    StrongExactAdapter,
    TokenIlikeMultiAdapter,
    TrigramMultiAdapter,
)
from app.rag.ocr.rrf_matcher import RrfMatcher

GT_JSONL = _HERE / "gt" / "prescriptions.jsonl"
REPORT_DIR = _PROJECT_ROOT / "reports" / "eval"
POSTGRES_DSN = os.getenv("POSTGRES_DSN", "postgresql://pillmate:pillmate_local@localhost:5433/pillmate")
BASELINE_HIT_AT_1 = 0.97
GATE_B_HIT_AT_1 = 0.84

# ── INN 키워드 (Gate B 와 동일 + gt_047 수정) ────────────────────────
_HARD_INN: dict[str, str] = {
    "gt_031": "세티리진", "gt_032": "암로디핀", "gt_033": "오메프라졸",
    "gt_034": "메트포르민", "gt_035": "로수바스타틴", "gt_047": "졸피뎀",  # ← 수정
    "gt_071": "타이레놀", "gt_072": "아목시실린", "gt_073": "이부프로펜",
    "gt_074": "아스피린", "gt_075": "메트포르민", "gt_076": "암로디핀",
    "gt_077": "로수바스타틴", "gt_078": "오메프라졸", "gt_079": "세티리진",
    "gt_080": "클래리스로마이신", "gt_086": "가바펜틴", "gt_087": "졸피뎀",
    "gt_088": "로페라미드", "gt_089": "글리메피리", "gt_090": "모사프리드",
    "gt_092": "에소메프라졸", "gt_096": "이부프로펜", "gt_097": "졸피뎀",
    "gt_098": "아목시실린", "gt_099": "메트포르민", "gt_100": "아목시실린",
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

_ENGLISH_UNIT_TOKENS = frozenset({"mg", "ml", "mcg", "ug", "g", "iu"})
_GENERAL_NAMES = frozenset({
    "혈압약", "위장약", "항히스타민제", "항히스타민", "당뇨약", "콜레스테롤약",
    "수면제", "진통소염제", "항생제", "항생체", "혈당강하제", "혈당약",
})


def _classify_input(name_raw: str) -> str:
    eng_words = [w for w in re.findall(r"[A-Za-z]{3,}", name_raw) if w.lower() not in _ENGLISH_UNIT_TOKENS]
    if eng_words:
        return "english"
    for gn in _GENERAL_NAMES:
        if gn in name_raw:
            return "general_name"
    ocr_typo_patterns = [r"[가-힣]+[픈렌멘심늘밍핀링]정", r"[가-힣]+[슬클]$", r"밀그램|밀그람"]
    for pat in ocr_typo_patterns:
        if re.search(pat, name_raw):
            return "ocr_typo"
    korean_chars = [c for c in name_raw if "가" <= c <= "힣"]
    if len(korean_chars) <= 4 and not re.search(r"\d", name_raw):
        return "abbreviated"
    return "long_compound"


def _auto_inn(gt_drug_name: str) -> str:
    m = re.match(r"^([가-힣]{2,})", gt_drug_name)
    if m:
        return m.group(1)[:4]
    return gt_drug_name[:4]


def _get_inn(gt_id: str, gt_drug_name: str) -> str:
    if gt_id in _HARD_INN:
        return _HARD_INN[gt_id]
    if gt_id in _MEDIUM_INN:
        return _MEDIUM_INN[gt_id]
    return _auto_inn(gt_drug_name)


def _is_hit(inn: str, candidate_name: str | None) -> bool:
    if not candidate_name or not inn:
        return False
    return inn in candidate_name


# ── RrfMatcher 조립 (StrongExactAdapter 사용) ────────────────────────
async def _build_matcher(pool) -> RrfMatcher:
    return RrfMatcher(
        exact_single=StrongExactAdapter(pool=pool),
        retrievers={
            "ilike": IlikeMultiAdapter(pool=pool),
            "trigram": TrigramMultiAdapter(
                trgm_search=TrigramFuzzySearch(pool=pool),
                ranker=JamoFuzzyRanker(),
            ),
            "token_ilike": TokenIlikeMultiAdapter(pool=pool),
            "prefix_relax": PrefixRelaxMultiAdapter(pool=pool),
            "ingredient": IngredientMultiAdapter(pool=pool),
        },
        reranker=DomainReranker(),
        bge_reranker=BgeRerankerAdapter(),
        decider=MatchDecider(),
    )


@dataclass
class RankRow:
    gt_id: str
    name_raw: str
    gt_drug_name: str
    difficulty: str
    input_type: str
    ranked_top1_name: str | None
    inn: str
    hit: bool
    final_score: float
    decision_type: str
    gate_b_was_miss: bool  # Gate B 에서 MISS 였던 항목


# Gate B miss 목록
_GATE_B_MISSES = frozenset({
    "gt_002", "gt_010", "gt_011", "gt_015", "gt_016",
    "gt_028", "gt_035", "gt_038", "gt_043", "gt_044",
    "gt_047", "gt_064", "gt_068", "gt_070", "gt_083", "gt_092",
})


async def run_eval(pool) -> list[RankRow]:
    matcher = await _build_matcher(pool)
    gt_items = [json.loads(l) for l in GT_JSONL.read_text().splitlines() if l.strip()]
    print(f"[INFO] GT {len(gt_items)}건 | StrongExactAdapter(Gate A+) | BGE(transformers 4.57.6)")
    rows: list[RankRow] = []

    for i, item in enumerate(gt_items):
        gt_id = item["id"]
        name_raw = item["name_raw"]
        gt_drug_name = item["drugs"][0]["name"]
        difficulty = item.get("difficulty", "easy")
        input_type = _classify_input(name_raw)
        was_miss = gt_id in _GATE_B_MISSES

        parsed = parse_drug_item(name_raw)
        try:
            result = await matcher.match(parsed)
        except Exception as exc:
            print(f"[ERROR] {gt_id}: {exc}")
            rows.append(RankRow(
                gt_id=gt_id, name_raw=name_raw, gt_drug_name=gt_drug_name,
                difficulty=difficulty, input_type=input_type,
                ranked_top1_name=None, inn="", hit=False, final_score=0.0,
                decision_type="ERROR", gate_b_was_miss=was_miss,
            ))
            continue

        decision = result.decision
        ranked_top1_name: str | None = None
        decision_type = "MANUAL"
        final_score = result.final_score or 0.0

        if decision is not None:
            decision_type = decision.type.value
            if decision.primary is not None:
                ranked_top1_name = decision.primary.name
            elif decision.options:
                ranked_top1_name = decision.options[0].name

        inn = _get_inn(gt_id, gt_drug_name)
        hit = _is_hit(inn, ranked_top1_name)

        change = ""
        if was_miss:
            change = "✓RECOVER" if hit else "✗STILL_MISS"
        elif not hit:
            change = "⚠REGRESS"

        print(f"  [{i+1:3d}/100] {gt_id} {'HIT ' if hit else 'MISS'} {decision_type:7s} {change:12s} {name_raw[:28]}")
        rows.append(RankRow(
            gt_id=gt_id, name_raw=name_raw, gt_drug_name=gt_drug_name,
            difficulty=difficulty, input_type=input_type,
            ranked_top1_name=ranked_top1_name, inn=inn, hit=hit,
            final_score=final_score, decision_type=decision_type,
            gate_b_was_miss=was_miss,
        ))
    return rows


def _format_report(rows: list[RankRow]) -> str:
    total = len(rows)
    hits = sum(1 for r in rows if r.hit)
    hit_at_1 = hits / total
    gate_b_misses = [r for r in rows if r.gate_b_was_miss]
    recovered = [r for r in gate_b_misses if r.hit]
    regressions = [r for r in rows if not r.gate_b_was_miss and not r.hit]
    new_misses = [r for r in rows if not r.hit]

    by_diff: dict[str, dict] = {}
    for r in rows:
        d = r.difficulty
        if d not in by_diff:
            by_diff[d] = {"total": 0, "hits": 0}
        by_diff[d]["total"] += 1
        by_diff[d]["hits"] += 1 if r.hit else 0

    by_type: dict[str, dict] = {}
    for r in rows:
        t = r.input_type
        if t not in by_type:
            by_type[t] = {"total": 0, "hits": 0}
        by_type[t]["total"] += 1
        by_type[t]["hits"] += 1 if r.hit else 0

    lines = [
        "# Gate B2 — StrongExactAdapter 순수 랭킹 Hit@1",
        "",
        f"> 날짜: 2026-06-13  ",
        f"> 변경: ExactIlikeAdapter → StrongExactAdapter (dose_amount gate 제거 + salt strip + prefix[:4])  ",
        f"> INN dict: gt_047 '졸피뎀타' → '졸피뎀' (주석산졸피뎀 형태 반영)  ",
        f"> Retriever: StrongExact · IlikeMulti · Trigram · TokenIlike · PrefixRelax · Ingredient  ",
        f"> Reranker: DomainReranker → BgeRerankerAdapter(normalize=True)  ",
        "",
        "## 1. 핵심 비교",
        "",
        "| 경로 | Hit@1 | 건수 |",
        "|------|-------|------|",
        f"| legacy cascade (기준) | {BASELINE_HIT_AT_1:.1%} | 97/100 |",
        f"| Gate B (RrfMatcher+GateA, ExactIlikeAdapter) | {GATE_B_HIT_AT_1:.1%} | 84/100 |",
        f"| **Gate B2 (StrongExactAdapter)** | **{hit_at_1:.1%}** | **{hits}/{total}** |",
        f"| vs legacy | {hit_at_1 - BASELINE_HIT_AT_1:+.1%} | {hits - 97:+d} |",
        f"| vs Gate B | {hit_at_1 - GATE_B_HIT_AT_1:+.1%} | {hits - 84:+d} |",
        "",
    ]

    lines += ["## 2. Difficulty별", "", "| Difficulty | 건수 | Hit | Hit@1 |", "|-----------|------|-----|-------|"]
    for diff in ["easy", "medium", "hard"]:
        d = by_diff.get(diff, {"total": 0, "hits": 0})
        rate = d["hits"] / d["total"] if d["total"] > 0 else 0.0
        lines.append(f"| {diff} | {d['total']} | {d['hits']} | {rate:.1%} |")
    lines.append("")

    lines += ["## 3. 입력 유형별 Hit@1", "", "| 유형 | 건수 | Hit | Hit@1 |", "|------|------|-----|-------|"]
    type_desc = {
        "long_compound": "정규 전문의약품명", "english": "영문 브랜드/성분명",
        "abbreviated": "짧은 한글명", "ocr_typo": "OCR 오탈자", "general_name": "일반 카테고리명",
    }
    for t in sorted(by_type.keys(), key=lambda x: -by_type[x]["total"]):
        d = by_type[t]
        rate = d["hits"] / d["total"] if d["total"] > 0 else 0.0
        lines.append(f"| {t} | {d['total']} | {d['hits']} | {rate:.1%} |")
    lines.append("")

    lines += [f"## 4. Gate B 대비 변화", ""]
    lines.append(f"### 4-1. 회복 항목 ({len(recovered)}/{len(gate_b_misses)}건 회복)")
    lines.append("")
    lines.append("| GT ID | name_raw | ranked_top1 | INN | Stage |")
    lines.append("|-------|----------|-------------|-----|-------|")
    for r in recovered:
        top1 = (r.ranked_top1_name or "(none)")[:35]
        lines.append(f"| {r.gt_id} | {r.name_raw[:28]} | {top1} | {r.inn} | {r.decision_type} |")
    lines.append("")

    if regressions:
        lines.append(f"### 4-2. ⚠ 회귀 항목 ({len(regressions)}건 — Gate B HIT → B2 MISS)")
        lines.append("")
        lines.append("| GT ID | name_raw | ranked_top1 | Score |")
        lines.append("|-------|----------|-------------|-------|")
        for r in regressions:
            top1 = (r.ranked_top1_name or "(none)")[:35]
            lines.append(f"| {r.gt_id} | {r.name_raw[:28]} | {top1} | {r.final_score:.3f} |")
        lines.append("")
    else:
        lines.append("### 4-2. ⚠ 회귀: **없음** ✓")
        lines.append("")

    lines += [f"## 5. 잔여 Miss ({len(new_misses)}건)", ""]
    lines.append("| GT ID | Difficulty | name_raw | ranked_top1 | INN | Score |")
    lines.append("|-------|-----------|----------|-------------|-----|-------|")
    for r in sorted(new_misses, key=lambda x: x.difficulty):
        top1 = (r.ranked_top1_name or "(none)")[:30]
        lines.append(f"| {r.gt_id} | {r.difficulty} | {r.name_raw[:28]} | {top1} | {r.inn} | {r.final_score:.3f} |")
    lines.append("")

    lines += ["## 6. CTO 판단 사항", ""]
    diff_vs_legacy = hit_at_1 - BASELINE_HIT_AT_1
    diff_vs_b = hit_at_1 - GATE_B_HIT_AT_1
    lines.append(f"- **Gate B2 순수 랭킹 Hit@1: {hit_at_1:.1%}** (+{diff_vs_b:.1%} vs Gate B)")
    lines.append(f"- vs legacy: {diff_vs_legacy:+.1%}")
    lines.append(f"- 회귀: {len(regressions)}건, 회복: {len(recovered)}건/{len(gate_b_misses)}건")
    if hit_at_1 >= BASELINE_HIT_AT_1:
        lines.append("- 랭킹 품질 ≥ legacy → **Gate C(임계 스윕) + Gate D(와이어링) 진행 가능**")
    else:
        lines.append(f"- 잔여 miss {len(new_misses)}건 — 원인: {', '.join(r.gt_id for r in new_misses)}")
    lines.append("")
    return "\n".join(lines)


async def main():
    print("=" * 70)
    print("Gate B2 — StrongExactAdapter 순수 랭킹 Hit@1 (main.py 무수정)")
    print("=" * 70)
    pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=2, max_size=5)
    try:
        rows = await run_eval(pool)
    finally:
        await pool.close()

    report_md = _format_report(rows)
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = REPORT_DIR / "gate_b2_ranking_2026-06-13.md"
    out_path.write_text(report_md, encoding="utf-8")

    total = len(rows)
    hits = sum(1 for r in rows if r.hit)
    gate_b_misses = [r for r in rows if r.gate_b_was_miss]
    recovered = [r for r in gate_b_misses if r.hit]
    regressions = [r for r in rows if not r.gate_b_was_miss and not r.hit]

    print()
    print("=" * 70)
    print("Gate B2 완료 — CTO 보고")
    print("=" * 70)
    print(f"  Hit@1: {hits/total:.1%} ({hits}/{total})  |  legacy: {BASELINE_HIT_AT_1:.1%}  |  Gate B: {GATE_B_HIT_AT_1:.1%}")
    print(f"  회복: {len(recovered)}/{len(gate_b_misses)}건  |  회귀: {len(regressions)}건")
    by_diff: dict[str, dict] = {}
    for r in rows:
        d = r.difficulty
        if d not in by_diff:
            by_diff[d] = {"total": 0, "hits": 0}
        by_diff[d]["total"] += 1
        by_diff[d]["hits"] += 1 if r.hit else 0
    for diff in ["easy", "medium", "hard"]:
        d = by_diff.get(diff, {"total": 0, "hits": 0})
        rate = d["hits"] / d["total"] if d["total"] > 0 else 0.0
        print(f"    {diff:6s}: {d['hits']}/{d['total']} = {rate:.1%}")
    if regressions:
        print(f"  ⚠ 회귀: {[r.gt_id for r in regressions]}")
    print(f"\n[SAVED] {out_path}")
    print("=" * 70)


if __name__ == "__main__":
    asyncio.run(main())
