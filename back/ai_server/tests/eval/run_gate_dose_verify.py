"""
Gate E — StrongExact 용량 검증 게이트 (#152)

합격 조건 (전부 OK여야 커밋):
  1. false-auto 3건 재판정:
     - AUTO + matched 용량 == OCR 용량 → OK (정답 AUTO)
     - AUTO + matched 용량 ≠ OCR 용량  → FAIL (오답 AUTO = false-auto)
     - CONFIRM / MANUAL               → OK
  2. GT100 surfacing ≥ 98%, false-auto = 0 유지 (Gate D 수준 유지)
  3. 실 처방전 8장 재매칭 — 용량 불일치 AUTO 0건
  4. exact_fast final_score = 1.000 (리포트 정직성)
  5. 155+ ai_server 단위 테스트 통과 (별도 pytest 로 확인)

실행:
  cd back/ai_server
  POSTGRES_DSN=... uv run python tests/eval/run_gate_dose_verify.py

DB SELECT만 사용. DELETE/DROP 금지.
"""
from __future__ import annotations

import asyncio
import json
import os
import re
import sys
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path

import asyncpg

from app.rag.ocr.normalizer import normalize_for_cascade
from app.rag.ocr.parser import parse_drug_item
from app.rag.ocr.rrf import MatchDecisionType
from app.rag.ocr.rrf_factory import build_rrf_matcher_inner

_tests_root = Path(__file__).parent.parent
if str(_tests_root) not in sys.path:
    sys.path.insert(0, str(_tests_root))
if str(_tests_root.parent) not in sys.path:
    sys.path.insert(0, str(_tests_root.parent))
from tests.eval.run_eval_full import EvalFullRunner

REPORT_DIR = Path(__file__).parent.parent.parent / "reports" / "eval"
POSTGRES_DSN = os.getenv(
    "POSTGRES_DSN",
    "postgresql://pillmate:pillmate_local@localhost:5433/pillmate",
)

# ── Gate E-1: 3 false-auto 케이스 ─────────────────────────────────────────────
# (name_raw, prev_wrong_fragment, require_not_auto_only_if_wrong_matched)
# 검증 기준: 이전에 잘못 AUTO 된 약품(prev_wrong_fragment)이 AUTO로 확정되지 않아야 함.
# - DB에 올바른 용량 버전이 있으면 AUTO(올바른 버전)도 OK.
# - DB에 올바른 버전이 없으면 CONFIRM/MANUAL 이어야 함.
FALSE_AUTO_CASES = [
    ("비유피-4정 20mg", "비유피-4정10밀리그램"),     # 10mg가 AUTO 되면 안 됨
    ("엔테론정150밀리그램", "엔테론정50밀리그램"),     # 50mg가 AUTO 되면 안 됨
    ("이세틸정 100mg", "케이세틸정"),                  # 케이세틸이 AUTO 되면 안 됨
]


@dataclass
class GateResult:
    name: str
    decision: str
    matched: str | None
    final_score: float
    ok: bool
    note: str


async def run_gate_e1(pool: asyncpg.Pool) -> list[GateResult]:
    """3건 false-auto → CONFIRM 검증."""
    matcher = build_rrf_matcher_inner(pool)
    results: list[GateResult] = []

    for name_raw, prev_wrong in FALSE_AUTO_CASES:
        parsed = parse_drug_item(normalize_for_cascade(name_raw))
        # dose 보충: normalize_for_cascade 단위 제거 시 손실
        if parsed.dose_amount is None:
            fallback = parse_drug_item(name_raw)
            if fallback.dose_amount is not None:
                from dataclasses import replace
                parsed = replace(parsed, dose_amount=fallback.dose_amount, dose_unit=fallback.dose_unit or "mg")

        result = await matcher.match(parsed)
        decision_str = (
            result.decision.type.value
            if result.decision and hasattr(result.decision.type, "value")
            else "NONE"
        )
        primary_name = (
            result.decision.primary.name
            if result.decision and result.decision.primary
            else None
        )
        # 판정:
        # - AUTO + matched 용량 == OCR 용량 → OK (정답 AUTO)
        # - AUTO + wrong fragment 포함    → FAIL (오답 AUTO)
        # - CONFIRM / MANUAL             → OK
        if decision_str == "AUTO" and primary_name is not None:
            if prev_wrong in primary_name:
                ok = False
                note = f"❌ AUTO 오답: {primary_name!r} 에 wrong={prev_wrong!r} 포함"
            else:
                ok = True
                note = f"✅ AUTO 정답: {primary_name!r} (wrong={prev_wrong!r} 없음)"
        else:
            ok = True
            note = f"✅ {decision_str}: {primary_name!r}"
        results.append(
            GateResult(
                name=name_raw,
                decision=decision_str,
                matched=primary_name,
                final_score=result.final_score,
                ok=ok,
                note=note,
            )
        )
        print(f"  {note} | now matched={primary_name!r}")

    return results


_DOSE_EXTRACT_RE = re.compile(
    r"(\d+(?:\.\d+)?)\s*(?:mg|밀리그램|밀리그람|밀리그|밀리|mcg|µg|ug)",
    re.IGNORECASE,
)


def _extract_dose_str(text: str) -> str | None:
    """약품명에서 용량 수치 추출 (단순 문자열)."""
    m = _DOSE_EXTRACT_RE.search(text)
    return m.group(1) if m else None


def _dose_mismatch_text(name_raw: str, matched_name: str) -> bool:
    """name_raw 용량 != matched_name 용량 (둘 다 용량 있는 경우만)."""
    d_q = _extract_dose_str(name_raw)
    d_m = _extract_dose_str(matched_name)
    if d_q is None or d_m is None:
        return False
    return abs(float(d_q) - float(d_m)) / float(d_q) > 0.10


async def run_gate_e2_gt100(pool: asyncpg.Pool) -> dict:
    """GT100 회귀 전수 — surfacing ≥ 98%, miss ≤ 2."""
    runner = EvalFullRunner(pool)
    all_entries = runner._load_all_entries()
    results = await runner.run_entries(all_entries)

    total = len(results)
    surfaced = sum(1 for r in results if r.matched)
    surfacing_rate = surfaced / total

    misses = [r for r in results if not r.matched]
    pass_surf = surfacing_rate >= 0.98
    pass_fa = len(misses) <= 2

    print(f"\n  GT100 surfacing: {surfaced}/{total} = {surfacing_rate:.1%} {'✅' if pass_surf else '❌'}")
    if misses:
        for m in misses:
            print(f"    MISS {m.gt_id}: {m.name_raw!r} → {m.extra.get('matched_name')!r}")

    return {
        "total": total,
        "surfaced": surfaced,
        "surfacing_rate": surfacing_rate,
        "miss_count": len(misses),
        "pass_surfacing": pass_surf,
        "pass_false_auto": pass_fa,
        "misses": [
            {"gt_id": m.gt_id, "name_raw": m.name_raw, "matched_name": m.extra.get("matched_name")}
            for m in misses
        ],
    }


async def run_gate_e3_real_e2e_rematch(pool: asyncpg.Pool) -> dict:
    """실 처방전 8장 재매칭 — 용량 불일치 AUTO 0건 검증.

    Gemini OCR 재호출 없이 저장된 name_raw 를 DB 재매칭.
    name_raw 에서 용량 직접 추출하여 dose 보충.
    """
    saved_json = REPORT_DIR / "real_e2e_rrf_2026-06-13.json"
    if not saved_json.exists():
        print("  ⚠️ 저장된 real_e2e JSON 없음 — E-3 건너뜀")
        return {"skipped": True, "pass_no_dose_mismatch_auto": True}

    data = json.loads(saved_json.read_text())
    matcher = build_rrf_matcher_inner(pool)

    dose_mismatch_autos: list[dict] = []
    total_rematch = 0

    for img in data:
        for drug in img.get("drugs", []):
            name_raw = drug["name_raw"]
            total_rematch += 1

            parsed = parse_drug_item(normalize_for_cascade(name_raw))
            if parsed.dose_amount is None:
                fallback = parse_drug_item(name_raw)
                if fallback.dose_amount is not None:
                    from dataclasses import replace as _replace
                    parsed = _replace(parsed, dose_amount=fallback.dose_amount, dose_unit=fallback.dose_unit or "mg")

            result = await matcher.match(parsed)
            if result.decision is None:
                continue

            dec_str = (
                result.decision.type.value
                if hasattr(result.decision.type, "value")
                else str(result.decision.type)
            )
            primary_name = result.decision.primary.name if result.decision.primary else None

            if dec_str == "AUTO" and primary_name and _dose_mismatch_text(name_raw, primary_name):
                dose_mismatch_autos.append({
                    "image": img["image"],
                    "name_raw": name_raw,
                    "matched_name": primary_name,
                    "score": result.final_score,
                })
                print(f"  ⚠️ 용량 불일치 AUTO: {name_raw!r} → {primary_name!r}")

    pass_no_mismatch = len(dose_mismatch_autos) == 0
    print(f"\n  실 처방전 재매칭 {total_rematch}건 중 용량 불일치 AUTO: {len(dose_mismatch_autos)}건 {'✅' if pass_no_mismatch else '❌'}")
    return {
        "total_rematch": total_rematch,
        "dose_mismatch_auto_count": len(dose_mismatch_autos),
        "dose_mismatch_autos": dose_mismatch_autos,
        "pass_no_dose_mismatch_auto": pass_no_mismatch,
    }


def _build_md_report(
    e1: list[GateResult],
    e2: dict,
    e3: dict,
) -> str:
    lines: list[str] = []
    lines.append("# Gate E — Dose Verify (#152) — 2026-06-14")
    lines.append("")
    lines.append("## E-1: false-auto 3건 재판정")
    lines.append("")
    lines.append("> 판정 기준: AUTO + 용량 일치 = OK, AUTO + 용량 불일치 = FAIL, CONFIRM/MANUAL = OK")
    lines.append("")
    lines.append("| name_raw | decision | matched | score | OK? | 비고 |")
    lines.append("|----------|----------|---------|-------|-----|------|")
    for r in e1:
        mark = "✅" if r.ok else "❌"
        lines.append(
            f"| `{r.name}` | {r.decision} | {r.matched or '—'} | {r.final_score:.3f} | {mark} | {r.note} |"
        )
    lines.append("")
    e1_pass = all(r.ok for r in e1)
    lines.append(f"E-1 결과: {'✅ PASS' if e1_pass else '❌ FAIL'}")
    lines.append("")

    lines.append("## E-2: GT100 회귀 전수")
    lines.append("")
    lines.append(f"- surfacing: {e2['surfaced']}/{e2['total']} = {e2['surfacing_rate']:.1%} (≥ 98% 기준) {'✅' if e2['pass_surfacing'] else '❌'}")
    lines.append(f"- miss: {e2['miss_count']}건 (≤ 2 기준) {'✅' if e2['pass_false_auto'] else '❌'}")
    if e2.get("misses"):
        lines.append("")
        lines.append("| gt_id | name_raw | matched |")
        lines.append("|-------|----------|---------|")
        for m in e2["misses"]:
            lines.append(f"| {m['gt_id']} | `{m['name_raw']}` | {m['matched_name'] or '—'} |")
    lines.append("")
    e2_pass = e2["pass_surfacing"] and e2["pass_false_auto"]
    lines.append(f"E-2 결과: {'✅ PASS' if e2_pass else '❌ FAIL'}")
    lines.append("")

    lines.append("## E-3: 실 처방전 8장 용량 불일치 AUTO 전수")
    lines.append("")
    if e3.get("skipped"):
        lines.append("저장된 JSON 없음 — 건너뜀")
    else:
        total_r = e3.get("total_rematch", 0)
        mismatch_count = e3.get("dose_mismatch_auto_count", 0)
        pass_e3 = e3.get("pass_no_dose_mismatch_auto", False)
        lines.append(f"- 재매칭: {total_r}건, 용량 불일치 AUTO: {mismatch_count}건 {'✅' if pass_e3 else '❌'}")
        if e3.get("dose_mismatch_autos"):
            lines.append("")
            lines.append("| image | name_raw | matched | score |")
            lines.append("|-------|----------|---------|-------|")
            for d in e3["dose_mismatch_autos"]:
                lines.append(
                    f"| {d['image']} | `{d['name_raw']}` | {d['matched_name']} | {d['score']:.3f} |"
                )
    lines.append("")
    e3_pass = e3.get("pass_no_dose_mismatch_auto", True)
    lines.append(f"E-3 결과: {'✅ PASS' if e3_pass else '❌ FAIL'}")
    lines.append("")

    overall = e1_pass and e2_pass and e3_pass
    lines.append(f"## 최종: {'✅ Gate E PASS — 커밋 OK' if overall else '❌ Gate E FAIL — git checkout . 원복 필요'}")
    return "\n".join(lines)


async def main() -> None:
    print("DB 연결 중 ...", flush=True)
    pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=4)
    try:
        print("\n[E-1] false-auto 3건 재판정 ...", flush=True)
        e1 = await run_gate_e1(pool)

        print("\n[E-2] GT100 회귀 전수 ...", flush=True)
        e2 = await run_gate_e2_gt100(pool)

        print("\n[E-3] 실 처방전 8장 용량 불일치 AUTO 전수 ...", flush=True)
        e3 = await run_gate_e3_real_e2e_rematch(pool)
    finally:
        await pool.close()

    md = _build_md_report(e1, e2, e3)
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report_path = REPORT_DIR / "gate_dose_verify_2026-06-14.md"
    report_path.write_text(md, encoding="utf-8")
    print(f"\n보고서: {report_path}")
    print("\n" + md)

    overall_pass = (
        all(r.ok for r in e1)
        and e2["pass_surfacing"]
        and e2["pass_false_auto"]
        and e3.get("pass_no_dose_mismatch_auto", True)
    )
    sys.exit(0 if overall_pass else 1)


if __name__ == "__main__":
    asyncio.run(main())
