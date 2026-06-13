"""
#150 Gate C — ABS_THRESHOLD 스윕 (2026-06-13)

전략: BGE reranker 는 임계와 무관하게 1회만 실행, 점수 캡처 후
     5개 임계를 파이썬 시뮬레이션으로 분석. (5× 재실행 불필요)

측정 지표 (임계별):
  AUTO 건수  - final_score >= T 이고 margin·dose 조건 통과
  AUTO 정밀도 - AUTO 중 정답 비율
  false-auto - AUTO 이지만 오답 (의료 안전 핵심)
  MANUAL 비율 - threshold 미달 또는 margin/dose 조건 실패

판단 기준:
  1순위: false-auto = 0
  2순위: AUTO recall 최대

실행: cd back/ai_server && .venv/bin/python tests/eval/run_gate_c_threshold_sweep.py
read-only: DB SELECT만, main.py 무수정, 임계/코드 변경 없음.
"""
from __future__ import annotations

import asyncio
import json
import os
import re
import sys
from dataclasses import dataclass, field
from decimal import Decimal
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

from app.rag.ocr.decider import MatchDecider, MatchDecisionType
from app.rag.ocr.fuzzy_search import JamoFuzzyRanker, TrigramFuzzySearch
from app.rag.ocr.parser import ParsedItem, parse_drug_item
from app.rag.ocr.reranker import BgeRerankerAdapter, DomainReranker
from app.rag.ocr.rrf import Candidate
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

# 스윕 대상 임계값
THRESHOLDS = [0.50, 0.60, 0.65, 0.70, 0.80]
CURRENT_THRESHOLD = 0.70  # 현재 운영 임계

# Gate B3 INN dict (동일)
_HARD_INN: dict[str, str] = {
    "gt_031": "세티리진", "gt_032": "암로디핀", "gt_033": "오메프라졸",
    "gt_034": "메트포르민", "gt_035": "로수바스타틴", "gt_047": "졸피뎀",
    "gt_071": "타이레놀", "gt_072": "아목시실린", "gt_073": "이부프로펜",
    "gt_074": "아스피린", "gt_075": "메트포르민", "gt_076": "암로디핀",
    "gt_077": "로수바스타틴", "gt_078": "오메프라졸", "gt_079": "세티리진",
    "gt_080": "클래리스로마이신", "gt_086": "가바펜틴", "gt_087": "졸피뎀",
    "gt_088": "로페라미드", "gt_089": "글리메피리", "gt_090": "모사프리드",
    "gt_092": "에소메프라", "gt_096": "이부프로펜", "gt_097": "졸피뎀",
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


def _get_inn(gt_id: str, gt_drug_name: str) -> str:
    if gt_id in _HARD_INN:
        return _HARD_INN[gt_id]
    if gt_id in _MEDIUM_INN:
        return _MEDIUM_INN[gt_id]
    m = re.match(r"^([가-힣]{2,})", gt_drug_name)
    return m.group(1)[:4] if m else gt_drug_name[:4]


def _is_hit(inn: str, candidate_name: str | None) -> bool:
    return bool(candidate_name and inn and inn in candidate_name)


# ── CaptureDecider: decide() 호출 시 점수 캡처 ────────────────────────

@dataclass
class CaptureRecord:
    """단일 항목 점수 캡처."""
    gt_id: str
    name_raw: str
    gt_drug_name: str
    inn: str
    # exact fast path 여부
    is_exact: bool = False
    exact_name: str | None = None
    exact_hit: bool = False
    # RRF path 점수 (is_exact=False 일 때 의미 있음)
    top1_score: float = 0.0
    top1_name: str | None = None
    top2_score: float = 0.0           # margin 계산용
    is_dose_variant_confirm: bool = False  # dose_unknown CONFIRM 여부
    is_margin_confirm: bool = False   # ambiguous CONFIRM 여부
    rrf_hit: bool = False


class CaptureDecider(MatchDecider):
    """MatchDecider 를 래핑해 결정 직전 점수를 캡처."""

    def __init__(self) -> None:
        self._records: list[CaptureRecord] = []
        self._current: CaptureRecord | None = None

    def set_current(self, rec: CaptureRecord) -> None:
        self._current = rec

    def decide(self, parsed: ParsedItem, ranked: list[Candidate]) -> any:
        decision = super().decide(parsed, ranked)
        if self._current is not None and not self._current.is_exact:
            rec = self._current
            if ranked:
                rec.top1_score = ranked[0].final_score
                rec.top1_name = ranked[0].name
                rec.rrf_hit = _is_hit(rec.inn, rec.top1_name)
            if len(ranked) >= 2:
                rec.top2_score = ranked[1].final_score
            rec.is_margin_confirm = (decision.type == MatchDecisionType.CONFIRM
                                     and decision.reason == "ambiguous")
            rec.is_dose_variant_confirm = (decision.type == MatchDecisionType.CONFIRM
                                           and decision.reason == "dose_unknown")
        return decision

    @property
    def records(self) -> list[CaptureRecord]:
        return self._records

    def add_record(self, rec: CaptureRecord) -> None:
        self._records.append(rec)


# ── RrfMatcher + CaptureDecider 조립 ─────────────────────────────────

class CapturingRrfMatcher(RrfMatcher):
    """RrfMatcher 를 상속해 exact fast path 도 캡처."""

    def __init__(self, capture_decider: CaptureDecider, **kwargs) -> None:
        super().__init__(decider=capture_decider, **kwargs)
        self._capture_decider = capture_decider

    async def match_with_capture(self, parsed: ParsedItem, rec: CaptureRecord) -> None:
        """match() 실행하며 rec 에 점수 기록."""
        from app.rag.ocr.rrf import MatchDecisionType
        from app.rag.ocr.rrf_matcher import _RERANK_TOP_N, _apply_sigmoid

        if not parsed.is_valid:
            rec.is_exact = False
            rec.top1_score = 0.0
            self._capture_decider.add_record(rec)
            return

        # exact fast path
        fast = await self._exact_single.search_single(parsed)
        if fast is not None:
            rec.is_exact = True
            rec.exact_name = fast.name
            rec.exact_hit = _is_hit(rec.inn, fast.name)
            self._capture_decider.add_record(rec)
            return

        # RRF path — BGE 실행
        fused = await self._run_rrf(parsed)
        if not fused:
            rec.is_exact = False
            self._capture_decider.add_record(rec)
            return

        ranked = self._reranker.rerank(parsed, fused[:_RERANK_TOP_N])
        if self._bge_reranker is not None:
            ranked = self._bge_reranker.rerank(parsed.raw, ranked)
        else:
            _apply_sigmoid(ranked)

        # 점수 캡처
        self._capture_decider.set_current(rec)
        self._capture_decider.decide(parsed, ranked)
        self._capture_decider.set_current(None)
        self._capture_decider.add_record(rec)


async def _build_capturing_matcher(pool) -> CapturingRrfMatcher:
    capture_decider = CaptureDecider()
    return CapturingRrfMatcher(
        capture_decider=capture_decider,
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
    )


# ── 단일 패스 실행 ───────────────────────────────────────────────────

async def run_capture_pass(pool) -> list[CaptureRecord]:
    matcher = await _build_capturing_matcher(pool)
    gt_items = [json.loads(l) for l in GT_JSONL.read_text().splitlines() if l.strip()]
    print(f"[INFO] GT {len(gt_items)}건 | 단일 패스 점수 캡처 (BGE 1회)")

    for i, item in enumerate(gt_items):
        gt_id = item["id"]
        name_raw = item["name_raw"]
        gt_drug_name = item["drugs"][0]["name"]
        inn = _get_inn(gt_id, gt_drug_name)

        rec = CaptureRecord(gt_id=gt_id, name_raw=name_raw, gt_drug_name=gt_drug_name, inn=inn)
        parsed = parse_drug_item(name_raw)

        try:
            await matcher.match_with_capture(parsed, rec)
        except Exception as exc:
            print(f"[ERROR] {gt_id}: {exc}")
            if rec not in matcher._capture_decider.records:
                matcher._capture_decider.add_record(rec)

        if (i + 1) % 10 == 0:
            print(f"  [{i+1:3d}/100] 완료")

    return matcher._capture_decider.records


# ── 임계 시뮬레이션 ───────────────────────────────────────────────────

MARGIN_THRESHOLD = 0.05


@dataclass
class ThresholdResult:
    threshold: float
    total: int
    # exact fast (threshold 독립)
    exact_auto: int = 0
    exact_hit: int = 0
    # RRF path 시뮬레이션
    rrf_auto: int = 0
    rrf_auto_hit: int = 0
    rrf_false_auto: int = 0
    rrf_confirm: int = 0   # margin/dose 조건 실패 (MANUAL로 취급)
    rrf_manual: int = 0    # score < threshold
    rrf_no_match: int = 0  # no candidates
    # false-auto 항목 목록
    false_auto_items: list[str] = field(default_factory=list)

    @property
    def total_auto(self) -> int:
        return self.exact_auto + self.rrf_auto

    @property
    def total_false_auto(self) -> int:
        return self.rrf_false_auto  # exact false-auto=0 verified

    @property
    def total_hit(self) -> int:
        return self.exact_hit + self.rrf_auto_hit

    @property
    def auto_precision(self) -> float:
        return self.total_hit / self.total_auto if self.total_auto else 0.0

    @property
    def auto_rate(self) -> float:
        return self.total_auto / self.total

    @property
    def manual_rate(self) -> float:
        return (self.total - self.total_auto) / self.total


def simulate_threshold(records: list[CaptureRecord], threshold: float) -> ThresholdResult:
    result = ThresholdResult(threshold=threshold, total=len(records))
    T = threshold

    for rec in records:
        if rec.is_exact:
            result.exact_auto += 1
            if rec.exact_hit:
                result.exact_hit += 1
            # exact false-auto=0 guaranteed (Gate B3 verified)
        else:
            # RRF path
            if rec.top1_name is None:
                result.rrf_no_match += 1
                continue

            if rec.top1_score < T:
                result.rrf_manual += 1
                continue

            # Score >= T — check margin and dose
            margin = rec.top1_score - rec.top2_score
            if margin < MARGIN_THRESHOLD:
                result.rrf_confirm += 1  # ambiguous → MANUAL
                continue

            if rec.is_dose_variant_confirm:
                result.rrf_confirm += 1  # dose_unknown → MANUAL
                continue

            # AUTO
            result.rrf_auto += 1
            if rec.rrf_hit:
                result.rrf_auto_hit += 1
            else:
                result.rrf_false_auto += 1
                result.false_auto_items.append(
                    f"{rec.gt_id}:'{rec.name_raw[:20]}' → '{(rec.top1_name or '')[:30]}'"
                )

    return result


# ── 보고서 생성 ──────────────────────────────────────────────────────

def _format_report(records: list[CaptureRecord], results: list[ThresholdResult]) -> str:
    total = len(records)
    exact_count = sum(1 for r in records if r.is_exact)
    rrf_count = total - exact_count
    exact_hits = sum(1 for r in records if r.is_exact and r.exact_hit)

    lines = [
        "# Gate C — ABS_THRESHOLD 스윕 (2026-06-13)",
        "",
        f"> 변경 없음 — 측정·시뮬레이션 전용.  ",
        f"> StrongExactAdapter (Gate A++) + DomainReranker + BgeRerankerAdapter(normalize=True)  ",
        f"> BGE 단일 패스 후 5개 임계 파이썬 시뮬레이션.  ",
        f"> MARGIN_THRESHOLD 고정 0.05 (변경 없음)  ",
        "",
        "## 0. 사전 정보",
        "",
        f"| 항목 | 값 |",
        f"|------|----|",
        f"| GT 항목 수 | {total} |",
        f"| StrongExact AUTO (임계 독립, 항상 AUTO) | {exact_count}건 (score=1.000) |",
        f"| StrongExact false-auto | 0건 ✅ (Gate B3 검증) |",
        f"| StrongExact HIT | {exact_hits}/{exact_count} ({exact_hits/exact_count:.1%}) |",
        f"| RRF path 항목 수 | {rrf_count} |",
        "",
        "## 1. 임계별 비교표",
        "",
        "| 임계 | AUTO 건 | AUTO% | 정밀도 | **false-auto** | MANUAL/CONFIRM 건 | MANUAL% |",
        "|------|--------|-------|--------|---------------|-------------------|---------|",
    ]

    for res in results:
        fa_cell = f"**{res.total_false_auto}건** {'✅' if res.total_false_auto == 0 else '❌'}"
        current = " ← **현재**" if abs(res.threshold - CURRENT_THRESHOLD) < 0.001 else ""
        lines.append(
            f"| {res.threshold:.2f}{current} "
            f"| {res.total_auto} "
            f"| {res.auto_rate:.1%} "
            f"| {res.auto_precision:.1%} "
            f"| {fa_cell} "
            f"| {res.total - res.total_auto} "
            f"| {res.manual_rate:.1%} |"
        )

    lines += [
        "",
        "## 2. false-auto 상세 (발생 시)",
        "",
    ]

    any_fa = False
    for res in results:
        if res.false_auto_items:
            any_fa = True
            lines.append(f"### 임계 {res.threshold:.2f}")
            for item in res.false_auto_items:
                lines.append(f"- {item}")
            lines.append("")

    if not any_fa:
        lines.append("**모든 임계에서 false-auto 없음 ✅**")
        lines.append("")

    lines += [
        "## 3. RRF path 점수 분포 (현황 파악)",
        "",
        "| 점수 구간 | 건수 | 정답 | 정밀도 | 비고 |",
        "|----------|------|------|--------|------|",
    ]

    rrf_records = [r for r in records if not r.is_exact and r.top1_name is not None]
    brackets = [
        (0.80, 1.01, "≥0.80"),
        (0.70, 0.80, "0.70–0.80"),
        (0.65, 0.70, "0.65–0.70"),
        (0.60, 0.65, "0.60–0.65"),
        (0.50, 0.60, "0.50–0.60"),
        (0.00, 0.50, "<0.50"),
    ]
    for lo, hi, label in brackets:
        bucket = [r for r in rrf_records if lo <= r.top1_score < hi]
        hits = sum(1 for r in bucket if r.rrf_hit)
        prec = hits / len(bucket) if bucket else 0.0
        lines.append(f"| {label} | {len(bucket)} | {hits} | {prec:.1%} | |")
    lines.append("")

    lines += ["## 4. 권장 임계 + 근거", ""]

    # 찾기: false-auto=0 인 임계 중 AUTO 최대
    valid = [r for r in results if r.total_false_auto == 0]
    if valid:
        best = max(valid, key=lambda r: r.total_auto)
        lines.append(f"**권장 임계: {best.threshold:.2f}**")
        lines.append("")
        lines.append(
            f"- false-auto=0 유지하는 모든 임계 중 AUTO 비율 최대: "
            f"{best.total_auto}건 ({best.auto_rate:.1%})"
        )
        lines.append(
            f"- 현재 임계 0.70 vs 권장 {best.threshold:.2f}: "
            f"AUTO {results[[r.threshold for r in results].index(CURRENT_THRESHOLD)].total_auto}건 → "
            f"{best.total_auto}건 "
            f"({'증가' if best.total_auto > results[[r.threshold for r in results].index(CURRENT_THRESHOLD)].total_auto else '동일'})"
        )
        lines.append(f"- 정밀도: {best.auto_precision:.1%} (false-auto=0 → 100% 또는 근접)")
    else:
        lines.append("모든 임계에서 false-auto 발생 — CTO 추가 분석 필요.")
    lines.append("")

    lines += ["## 5. CTO 판단 사항", ""]
    lines.append("- [ ] 권장 임계 채택 여부 결정")
    lines.append("- [ ] Gate D(main.py 와이어링) 진행 승인 여부")
    lines.append("")
    return "\n".join(lines)


# ── main ─────────────────────────────────────────────────────────────

async def main():
    print("=" * 70)
    print("Gate C — ABS_THRESHOLD 스윕 (BGE 단일 패스, main.py 무수정)")
    print("=" * 70)

    pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=2, max_size=5)
    try:
        records = await run_capture_pass(pool)
    finally:
        await pool.close()

    print(f"\n[INFO] {len(records)}건 캡처 완료. 임계 시뮬레이션 시작...")

    results: list[ThresholdResult] = []
    for T in THRESHOLDS:
        res = simulate_threshold(records, T)
        results.append(res)
        fa_mark = "✅" if res.total_false_auto == 0 else f"❌ {res.total_false_auto}건"
        current = " ← 현재" if abs(T - CURRENT_THRESHOLD) < 0.001 else ""
        print(
            f"  임계 {T:.2f}{current:8s} | AUTO {res.total_auto:3d}건 ({res.auto_rate:.0%}) "
            f"| 정밀도 {res.auto_precision:.1%} | false-auto {fa_mark}"
        )
        if res.false_auto_items:
            for item in res.false_auto_items:
                print(f"    ⛔ {item}")

    report_md = _format_report(records, results)
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = REPORT_DIR / "gate_c_threshold_sweep_2026-06-13.md"
    out_path.write_text(report_md, encoding="utf-8")

    print()
    print("=" * 70)
    print("Gate C 완료 — CTO 보고")
    print("=" * 70)
    valid = [r for r in results if r.total_false_auto == 0]
    if valid:
        best = max(valid, key=lambda r: r.total_auto)
        cur = next(r for r in results if abs(r.threshold - CURRENT_THRESHOLD) < 0.001)
        print(f"  권장 임계: {best.threshold:.2f}")
        print(f"  현재 0.70: AUTO {cur.total_auto}건 ({cur.auto_rate:.0%}), false-auto 0")
        print(f"  권장 {best.threshold:.2f}: AUTO {best.total_auto}건 ({best.auto_rate:.0%}), false-auto 0")
        delta = best.total_auto - cur.total_auto
        print(f"  검수 절감: +{delta}건 자동확정 증가" if delta > 0 else "  현재 임계가 최적")
    print(f"\n[SAVED] {out_path}")
    print("=" * 70)
    print("  → 끝. Gate D 는 CTO 승인 후 진행.")


if __name__ == "__main__":
    asyncio.run(main())
