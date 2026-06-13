"""
#150 Gate A++ → Gate B3 — StrongExactAdapter false-auto 제거 후 순수 랭킹 Hit@1 (2026-06-13)

Gate A++ 변경 사항:
  - StrongExactAdapter._build_queries: (query, require_main_hit) 튜플 반환
  - prefix[:4] 쿼리: require_main_hit=True — main_name 미포함 시 None → RRF 위임
  - cascade/stripped/token 쿼리: require_main_hit=False — INN 수준 → 괄호 허용
  - 영문 입력 prefix guard 유지 (Gate A+ 이후)

INN dict 수정:
  - gt_092 "에소메프라졸" → "에소메프라" (DB명 "에소메프라정" ↔ "에스오메프라졸" 철자 차이 해소)

실행: cd back/ai_server && .venv/bin/python tests/eval/run_gate_b3_ranking.py
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
GATE_B2_HIT_AT_1 = 0.97

# ── INN 키워드 ─────────────────────────────────────────────────────────
# gt_092: "에소메프라졸" → "에소메프라"
#   이유: DB명 "에소메프라정(에스오메프라졸...)" — main은 '에소메프라정'(졸 없음),
#         괄호는 '에스오메프라졸'(에스오). '에소메프라졸' substring 불일치.
#         '에소메프라'(6자)는 "에소메프라정..." 에 포함 → 올바른 정답 약 식별 가능.
_HARD_INN: dict[str, str] = {
    "gt_031": "세티리진", "gt_032": "암로디핀", "gt_033": "오메프라졸",
    "gt_034": "메트포르민", "gt_035": "로수바스타틴", "gt_047": "졸피뎀",
    "gt_071": "타이레놀", "gt_072": "아목시실린", "gt_073": "이부프로펜",
    "gt_074": "아스피린", "gt_075": "메트포르민", "gt_076": "암로디핀",
    "gt_077": "로수바스타틴", "gt_078": "오메프라졸", "gt_079": "세티리진",
    "gt_080": "클래리스로마이신", "gt_086": "가바펜틴", "gt_087": "졸피뎀",
    "gt_088": "로페라미드", "gt_089": "글리메피리", "gt_090": "모사프리드",
    "gt_092": "에소메프라",   # ← Gate B3 수정: 에소메프라졸 → 에소메프라
    "gt_096": "이부프로펜", "gt_097": "졸피뎀",
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


# ── RrfMatcher 조립 ────────────────────────────────────────────────────
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
    gate_b2_was_miss: bool  # Gate B2 에서 MISS 였던 항목


# Gate B2 miss 목록 (gt_016, gt_035, gt_092)
_GATE_B2_MISSES = frozenset({"gt_016", "gt_035", "gt_092"})


async def run_eval(pool) -> list[RankRow]:
    matcher = await _build_matcher(pool)
    gt_items = [json.loads(l) for l in GT_JSONL.read_text().splitlines() if l.strip()]
    print(f"[INFO] GT {len(gt_items)}건 | StrongExactAdapter(Gate A++) | BGE(transformers 4.57.6)")
    rows: list[RankRow] = []

    for i, item in enumerate(gt_items):
        gt_id = item["id"]
        name_raw = item["name_raw"]
        gt_drug_name = item["drugs"][0]["name"]
        difficulty = item.get("difficulty", "easy")
        input_type = _classify_input(name_raw)
        was_miss = gt_id in _GATE_B2_MISSES

        parsed = parse_drug_item(name_raw)
        try:
            result = await matcher.match(parsed)
        except Exception as exc:
            print(f"[ERROR] {gt_id}: {exc}")
            rows.append(RankRow(
                gt_id=gt_id, name_raw=name_raw, gt_drug_name=gt_drug_name,
                difficulty=difficulty, input_type=input_type,
                ranked_top1_name=None, inn="", hit=False, final_score=0.0,
                decision_type="ERROR", gate_b2_was_miss=was_miss,
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
        is_false_auto = (decision_type == "AUTO") and (not hit)

        change = ""
        if was_miss:
            change = "✓RECOVER" if hit else "✗STILL_MISS"
        elif not hit:
            change = "⚠REGRESS"

        fa_tag = " ⛔FALSE_AUTO" if is_false_auto else ""
        print(f"  [{i+1:3d}/100] {gt_id} {'HIT ' if hit else 'MISS'} {decision_type:7s} {change:12s}{fa_tag} {name_raw[:28]}")
        rows.append(RankRow(
            gt_id=gt_id, name_raw=name_raw, gt_drug_name=gt_drug_name,
            difficulty=difficulty, input_type=input_type,
            ranked_top1_name=ranked_top1_name, inn=inn, hit=hit,
            final_score=final_score, decision_type=decision_type,
            gate_b2_was_miss=was_miss,
        ))
    return rows


def _format_report(rows: list[RankRow]) -> str:
    total = len(rows)
    hits = sum(1 for r in rows if r.hit)
    hit_at_1 = hits / total
    gate_b2_misses = [r for r in rows if r.gate_b2_was_miss]
    recovered = [r for r in gate_b2_misses if r.hit]
    regressions = [r for r in rows if not r.gate_b2_was_miss and not r.hit]
    new_misses = [r for r in rows if not r.hit]

    # false-auto = AUTO 결정이지만 정답 아님
    false_autos = [r for r in rows if r.decision_type == "AUTO" and not r.hit]

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
        "# Gate B3 — StrongExactAdapter (Gate A++) 순수 랭킹 Hit@1",
        "",
        f"> 날짜: 2026-06-13  ",
        f"> 변경 (Gate A++): prefix[:4] 쿼리에 require_main_hit=True 플래그.  ",
        f">   괄호 앞 main_name 미포함 시 단축 금지 → RRF 위임 (false-auto 방지).  ",
        f">   cascade/stripped/token 쿼리는 INN 수준 → 괄호 내 성분명 매칭 허용 유지.  ",
        f"> INN dict: gt_092 '에소메프라졸' → '에소메프라' (에소↔에스오 철자 mismatch 해소)  ",
        f"> Retriever: StrongExact · IlikeMulti · Trigram · TokenIlike · PrefixRelax · Ingredient  ",
        f"> Reranker: DomainReranker → BgeRerankerAdapter(normalize=True)  ",
        "",
        "## 0. 최우선 지표 — false-auto",
        "",
        f"| 지표 | 값 |",
        f"|------|----|",
        f"| **false-auto 건수** | **{len(false_autos)}건** {'✅ 목표 달성' if len(false_autos) == 0 else '❌ 잔여'} |",
        f"| Hit@1 | {hit_at_1:.1%} ({hits}/{total}) |",
        f"| legacy 대비 | {hit_at_1 - BASELINE_HIT_AT_1:+.1%} |",
        "",
    ]

    if false_autos:
        lines.append("### false-auto 상세")
        lines.append("")
        lines.append("| GT ID | name_raw | AUTO 결과 (틀린 약) | Score |")
        lines.append("|-------|----------|---------------------|-------|")
        for r in false_autos:
            top1 = (r.ranked_top1_name or "(none)")[:40]
            lines.append(f"| {r.gt_id} | {r.name_raw[:28]} | {top1} | {r.final_score:.3f} |")
        lines.append("")
    else:
        lines.append("### false-auto: **없음** ✅")
        lines.append("")

    lines += [
        "## 1. 핵심 비교",
        "",
        "| 경로 | Hit@1 | 건수 |",
        "|------|-------|------|",
        f"| legacy cascade (기준) | {BASELINE_HIT_AT_1:.1%} | 97/100 |",
        f"| Gate B (RrfMatcher+GateA, ExactIlikeAdapter) | {GATE_B_HIT_AT_1:.1%} | 84/100 |",
        f"| Gate B2 (StrongExactAdapter) | {GATE_B2_HIT_AT_1:.1%} | 97/100 |",
        f"| **Gate B3 (StrongExact Gate A++)** | **{hit_at_1:.1%}** | **{hits}/{total}** |",
        f"| vs legacy | {hit_at_1 - BASELINE_HIT_AT_1:+.1%} | {hits - 97:+d} |",
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
        desc = type_desc.get(t, t)
        lines.append(f"| {t} ({desc}) | {d['total']} | {d['hits']} | {rate:.1%} |")
    lines.append("")

    lines += [f"## 4. Gate B2 대비 변화 (잔여 miss {len(_GATE_B2_MISSES)}건 기준)", ""]
    lines.append(f"### 4-1. 회복 항목 ({len(recovered)}/{len(gate_b2_misses)}건 회복)")
    lines.append("")
    lines.append("| GT ID | name_raw | ranked_top1 | INN | Stage |")
    lines.append("|-------|----------|-------------|-----|-------|")
    for r in recovered:
        top1 = (r.ranked_top1_name or "(none)")[:35]
        lines.append(f"| {r.gt_id} | {r.name_raw[:28]} | {top1} | {r.inn} | {r.decision_type} |")
    lines.append("")

    if regressions:
        lines.append(f"### 4-2. ⚠ 회귀 항목 ({len(regressions)}건 — Gate B2 HIT → B3 MISS)")
        lines.append("")
        lines.append("| GT ID | name_raw | ranked_top1 | Score |")
        lines.append("|-------|----------|-------------|-------|")
        for r in regressions:
            top1 = (r.ranked_top1_name or "(none)")[:35]
            lines.append(f"| {r.gt_id} | {r.name_raw[:28]} | {top1} | {r.final_score:.3f} |")
        lines.append("")
    else:
        lines.append("### 4-2. 회귀: **없음** ✅")
        lines.append("")

    lines += [f"## 5. 잔여 Miss ({len(new_misses)}건)", ""]
    lines.append("| GT ID | Difficulty | 입력유형 | name_raw | ranked_top1 | INN | Score | AUTO? |")
    lines.append("|-------|-----------|---------|----------|-------------|-----|-------|-------|")
    for r in sorted(new_misses, key=lambda x: x.difficulty):
        top1 = (r.ranked_top1_name or "(none)")[:30]
        auto_tag = "⛔FA" if r.decision_type == "AUTO" else ""
        lines.append(
            f"| {r.gt_id} | {r.difficulty} | {r.input_type} | {r.name_raw[:28]} "
            f"| {top1} | {r.inn} | {r.final_score:.3f} | {auto_tag} |"
        )
    lines.append("")

    lines += ["## 6. CTO 판단 사항", ""]
    lines.append(f"- **false-auto: {len(false_autos)}건** {'→ 의료 안전 목표 달성 ✅' if not false_autos else '→ 추가 수정 필요 ❌'}")
    lines.append(f"- **Gate B3 Hit@1: {hit_at_1:.1%}** (legacy {BASELINE_HIT_AT_1:.1%} 대비 {hit_at_1 - BASELINE_HIT_AT_1:+.1%})")
    lines.append(f"- 회귀: {len(regressions)}건, Gate B2 대비 신규 회복: {len(recovered)}건")
    if not false_autos and not regressions and hit_at_1 >= BASELINE_HIT_AT_1:
        lines.append("- false-auto 0 + 회귀 0 + Hit@1 ≥ legacy → **Gate C(임계 스윕) 진행 가능**")
    elif not false_autos and not regressions:
        lines.append(f"- false-auto 0 + 회귀 0 이지만 Hit@1 {hit_at_1:.1%} < legacy → CTO 판단 필요")
    else:
        lines.append("- 추가 수정 필요 → CTO 확인 후 결정")
    lines.append("")
    return "\n".join(lines)


async def main():
    print("=" * 70)
    print("Gate B3 — StrongExactAdapter (Gate A++) 순수 랭킹 Hit@1 (main.py 무수정)")
    print("=" * 70)
    pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=2, max_size=5)
    try:
        rows = await run_eval(pool)
    finally:
        await pool.close()

    report_md = _format_report(rows)
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = REPORT_DIR / "gate_b3_ranking_2026-06-13.md"
    out_path.write_text(report_md, encoding="utf-8")

    total = len(rows)
    hits = sum(1 for r in rows if r.hit)
    gate_b2_misses = [r for r in rows if r.gate_b2_was_miss]
    recovered = [r for r in gate_b2_misses if r.hit]
    regressions = [r for r in rows if not r.gate_b2_was_miss and not r.hit]
    false_autos = [r for r in rows if r.decision_type == "AUTO" and not r.hit]

    print()
    print("=" * 70)
    print("Gate B3 완료 — CTO 보고")
    print("=" * 70)
    print(f"  ⛔ false-auto: {len(false_autos)}건 {'← 목표 달성 ✅' if not false_autos else '← 잔여 ❌'}")
    if false_autos:
        for r in false_autos:
            print(f"    [{r.gt_id}] '{r.name_raw}' → {(r.ranked_top1_name or '?')[:40]} (score={r.final_score:.3f})")
    print(f"  Hit@1: {hits/total:.1%} ({hits}/{total})  |  legacy: {BASELINE_HIT_AT_1:.1%}  |  Gate B: {GATE_B_HIT_AT_1:.1%}")
    print(f"  Gate B2 대비 회복: {len(recovered)}/{len(gate_b2_misses)}건  |  회귀: {len(regressions)}건")
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
