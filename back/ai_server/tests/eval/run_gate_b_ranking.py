"""
#150 Gate B — 순수 랭킹 Hit@1 측정 (2026-06-13)

legacy run_eval_full.py 97% 와 동일 지표로 RrfMatcher(Gate A 어댑터 포함)를 비교.

지표 정의:
  순수 랭킹 Hit@1 = RRF+DomainReranker+BGE 후 ranked[0].name 에
                    INN 키워드가 포함되면 Hit (AUTO/CONFIRM/MANUAL 결정 무관).

  ※ item_seq = DB kd_code (drugs.kd_code).
     GT kd_code ≠ DB kd_code (같은 약 다른 제조사 코드) → kd_code 직접 비교 금지.
     legacy eval 동일 방식: _is_hit_by_inn(ranked[0].name, inn_keyword).

실행: cd back/ai_server && .venv/bin/python tests/eval/run_gate_b_ranking.py
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
POSTGRES_DSN = os.getenv("POSTGRES_DSN", "postgresql://pillmate:pillmate_local@localhost:5433/pillmate")
BASELINE_HIT_AT_1 = 0.97

# ── INN 키워드 (legacy eval 동일) ────────────────────────────────────
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

_ENGLISH_UNIT_TOKENS = frozenset({"mg", "ml", "mcg", "ug", "g", "iu"})

# ── 입력 유형 분류 ───────────────────────────────────────────────────
_GENERAL_NAMES = frozenset({
    "혈압약", "위장약", "항히스타민제", "항히스타민", "당뇨약", "콜레스테롤약",
    "수면제", "진통소염제", "항생제", "항생체", "혈당강하제", "혈당약",
})


def _classify_input(name_raw: str) -> str:
    """입력 유형 분류 (miss 원인 분석용)."""
    stripped = re.sub(r"\d+\s*(mg|ml|mcg|ug|g|iu|밀리그?[램람]|마이크로그램)", "", name_raw, flags=re.IGNORECASE).strip()

    eng_words = [w for w in re.findall(r"[A-Za-z]{3,}", name_raw) if w.lower() not in _ENGLISH_UNIT_TOKENS]
    if eng_words:
        return "english"

    for gn in _GENERAL_NAMES:
        if gn in name_raw:
            return "general_name"

    ocr_typo_patterns = [
        r"[가-힣]+[픈렌멘심늘밍핀링]정",
        r"[가-힣]+[슬클]$",
        r"밀그램|밀그람",
    ]
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


# ── RrfMatcher 조립 ─────────────────────────────────────────────────
async def _build_matcher(pool) -> RrfMatcher:
    exact = ExactIlikeAdapter(pool=pool)
    ilike_multi = IlikeMultiAdapter(pool=pool)
    trgm_multi = TrigramMultiAdapter(
        trgm_search=TrigramFuzzySearch(pool=pool),
        ranker=JamoFuzzyRanker(),
    )
    token_ilike = TokenIlikeMultiAdapter(pool=pool)
    prefix_relax = PrefixRelaxMultiAdapter(pool=pool)
    ingredient = IngredientMultiAdapter(pool=pool)

    return RrfMatcher(
        exact_single=exact,
        retrievers={
            "ilike": ilike_multi,
            "trigram": trgm_multi,
            "token_ilike": token_ilike,
            "prefix_relax": prefix_relax,
            "ingredient": ingredient,
        },
        reranker=DomainReranker(),
        bge_reranker=BgeRerankerAdapter(),
        decider=MatchDecider(),
    )


# ── 평가 행 ─────────────────────────────────────────────────────────
@dataclass
class RankRow:
    gt_id: str
    name_raw: str
    gt_drug_name: str
    difficulty: str
    input_type: str
    ranked_top1_name: str | None  # decision.options[0].name = ranked[0].name
    inn: str
    hit: bool
    final_score: float
    decision_type: str


async def run_eval(pool) -> list[RankRow]:
    matcher = await _build_matcher(pool)
    gt_items = [json.loads(l) for l in GT_JSONL.read_text().splitlines() if l.strip()]
    print(f"[INFO] GT {len(gt_items)}건 | RRF+Gate-A adapters | BGE(transformers 4.57.6)")
    rows: list[RankRow] = []

    for i, item in enumerate(gt_items):
        gt_id = item["id"]
        name_raw = item["name_raw"]
        gt_drug_name = item["drugs"][0]["name"]
        difficulty = item.get("difficulty", "easy")
        input_type = _classify_input(name_raw)

        parsed = parse_drug_item(name_raw)
        try:
            result = await matcher.match(parsed)
        except Exception as exc:
            print(f"[ERROR] {gt_id}: {exc}")
            rows.append(RankRow(
                gt_id=gt_id, name_raw=name_raw, gt_drug_name=gt_drug_name,
                difficulty=difficulty, input_type=input_type,
                ranked_top1_name=None, inn="", hit=False, final_score=0.0,
                decision_type="ERROR",
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

        status = "HIT " if hit else "MISS"
        print(f"  [{i+1:3d}/100] {gt_id} {status} {decision_type:7s} [{input_type:13s}] score={final_score:.3f}  {name_raw[:30]}")
        rows.append(RankRow(
            gt_id=gt_id, name_raw=name_raw, gt_drug_name=gt_drug_name,
            difficulty=difficulty, input_type=input_type,
            ranked_top1_name=ranked_top1_name, inn=inn, hit=hit,
            final_score=final_score, decision_type=decision_type,
        ))
    return rows


# ── 리포트 생성 ──────────────────────────────────────────────────────
def _format_report(rows: list[RankRow]) -> str:
    total = len(rows)
    hits = sum(1 for r in rows if r.hit)
    hit_at_1 = hits / total

    # difficulty
    by_diff: dict[str, dict] = {}
    for r in rows:
        d = r.difficulty
        if d not in by_diff:
            by_diff[d] = {"total": 0, "hits": 0}
        by_diff[d]["total"] += 1
        by_diff[d]["hits"] += 1 if r.hit else 0

    # input type
    by_type: dict[str, dict] = {}
    for r in rows:
        t = r.input_type
        if t not in by_type:
            by_type[t] = {"total": 0, "hits": 0}
        by_type[t]["total"] += 1
        by_type[t]["hits"] += 1 if r.hit else 0

    misses = [r for r in rows if not r.hit]
    miss_by_type: dict[str, list[RankRow]] = {}
    for r in misses:
        miss_by_type.setdefault(r.input_type, []).append(r)

    lines = [
        "# Gate B — 순수 랭킹 Hit@1 (RrfMatcher + Gate A adapters)",
        "",
        f"> 날짜: 2026-06-13  ",
        f"> 지표: ranked[0].name INN 포함 여부 (AUTO/CONFIRM/MANUAL 결정 무관)  ",
        f"> Retriever: ExactIlikeAdapter · IlikeMultiAdapter · TrigramMultiAdapter  ",
        f">            TokenIlikeMultiAdapter · PrefixRelaxMultiAdapter · IngredientMultiAdapter  ",
        f"> Reranker: DomainReranker → BgeRerankerAdapter(normalize=True, transformers 4.57.6)  ",
        f"> Hit 판정: _is_hit_by_inn(ranked_top1.name, inn_keyword) — legacy와 동일  ",
        f"> ABS_THRESHOLD: 사용 안함 (순수 랭킹 지표)  ",
        "",
        "## 1. 핵심 비교",
        "",
        "| 경로 | 지표 | Hit@1 | 건수 |",
        "|------|------|-------|------|",
        f"| legacy cascade (run_eval_full.py) | ranked[0] INN | {BASELINE_HIT_AT_1:.1%} | 97/100 |",
        f"| **RrfMatcher + Gate A** | **ranked[0] INN** | **{hit_at_1:.1%}** | **{hits}/{total}** |",
        f"| 차이 | | {hit_at_1 - BASELINE_HIT_AT_1:+.1%} | {hits - 97:+d} |",
        "",
    ]

    lines += ["## 2. Difficulty별 분해", "", "| Difficulty | 건수 | Hit | Hit@1 |", "|-----------|------|-----|-------|"]
    for diff in ["easy", "medium", "hard"]:
        d = by_diff.get(diff, {"total": 0, "hits": 0})
        rate = d["hits"] / d["total"] if d["total"] > 0 else 0.0
        lines.append(f"| {diff} | {d['total']} | {d['hits']} | {rate:.1%} |")
    lines.append("")

    lines += ["## 3. 입력 유형별 Hit@1", "", "| 유형 | 건수 | Hit | Hit@1 | 설명 |", "|------|------|-----|-------|------|"]
    type_desc = {
        "long_compound": "정규 전문의약품명 (full compound)",
        "english": "영문 브랜드/성분명 (Tylenol, Amoxicillin)",
        "abbreviated": "짧은 한글명 (타이레놀, 아목시)",
        "ocr_typo": "OCR 오탈자 (타이레늘, 아스피링)",
        "general_name": "일반 카테고리명 (혈압약, 위장약)",
    }
    for t in sorted(by_type.keys(), key=lambda x: -by_type[x]["total"]):
        d = by_type[t]
        rate = d["hits"] / d["total"] if d["total"] > 0 else 0.0
        desc = type_desc.get(t, t)
        lines.append(f"| {t} | {d['total']} | {d['hits']} | {rate:.1%} | {desc} |")
    lines.append("")

    lines += [f"## 4. Miss 목록 ({len(misses)}건) — 유형별", ""]
    for t in sorted(miss_by_type.keys()):
        t_misses = miss_by_type[t]
        lines.append(f"### {t} ({len(t_misses)}건)")
        lines.append("")
        lines.append("| GT ID | Difficulty | name_raw | ranked_top1 | Score |")
        lines.append("|-------|-----------|----------|-------------|-------|")
        for r in sorted(t_misses, key=lambda x: x.difficulty):
            top1 = (r.ranked_top1_name or "(none)")[:30]
            lines.append(f"| {r.gt_id} | {r.difficulty} | {r.name_raw[:30]} | {top1} | {r.final_score:.3f} |")
        lines.append("")

    # CTO 판단
    diff_pct = hit_at_1 - BASELINE_HIT_AT_1
    lines += ["## 5. CTO 판단 사항", ""]
    lines.append(f"- **RrfMatcher 순수 랭킹 Hit@1: {hit_at_1:.1%}** vs legacy {BASELINE_HIT_AT_1:.1%} ({diff_pct:+.1%})")
    if hit_at_1 >= BASELINE_HIT_AT_1:
        lines.append("- 랭킹 품질 ≥ legacy → **Gate C(임계 스윕) + Gate D(와이어링) 진행 가능**")
    else:
        lines.append(f"- 랭킹 품질 legacy 대비 {abs(diff_pct):.1%} 부족 → miss 원인 분석 후 추가 보강 검토")
    lines.append("")
    lines.append("**Miss 원인별 개요:**")
    for t in sorted(miss_by_type.keys()):
        n = len(miss_by_type[t])
        lines.append(f"- `{t}`: {n}건")
    lines.append("")
    return "\n".join(lines)


async def main():
    print("=" * 65)
    print("Gate B — 순수 랭킹 Hit@1 (legacy 동일 지표, main.py 무수정)")
    print("=" * 65)
    pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=2, max_size=5)
    try:
        rows = await run_eval(pool)
    finally:
        await pool.close()

    report_md = _format_report(rows)
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = REPORT_DIR / "gate_b_ranking_2026-06-13.md"
    out_path.write_text(report_md, encoding="utf-8")

    total = len(rows)
    hits = sum(1 for r in rows if r.hit)
    misses = [r for r in rows if not r.hit]

    print()
    print("=" * 65)
    print(report_md)
    print("=" * 65)
    print(f"\n[SAVED] {out_path}")
    print()
    print("=" * 65)
    print("Gate B 완료 — CTO 보고")
    print("=" * 65)

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

    print(f"  (1) RrfMatcher 순수 랭킹 Hit@1: {hits/total:.1%} ({hits}/{total})")
    print(f"  (1) legacy baseline:             {BASELINE_HIT_AT_1:.1%}")
    print(f"  (1) 차이:                        {hits/total - BASELINE_HIT_AT_1:+.1%}")
    print(f"  (2) Difficulty:")
    for diff in ["easy", "medium", "hard"]:
        d = by_diff.get(diff, {"total": 0, "hits": 0})
        rate = d["hits"] / d["total"] if d["total"] > 0 else 0.0
        print(f"      {diff:6s}: {d['hits']}/{d['total']} = {rate:.1%}")
    print(f"  (3) Miss {len(misses)}건 원인:")
    for t in sorted(by_type.keys(), key=lambda x: -by_type[x].get("total", 0)):
        d = by_type[t]
        miss_n = d["total"] - d["hits"]
        if miss_n > 0:
            rate = d["hits"] / d["total"] if d["total"] > 0 else 0.0
            print(f"      {t:15s}: {d['total']:2d}건 중 {miss_n:2d}건 miss  (Hit {rate:.0%})")
    print("=" * 65)


if __name__ == "__main__":
    asyncio.run(main())
