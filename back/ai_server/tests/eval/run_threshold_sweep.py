"""
#148 T-AI-THRESHOLD-SWEEP — 자동확정 임계값 데이터 측정 (2026-06-13)

ABS_THRESHOLD=0.70 이 데이터 튜닝 안 됨 → GT 100건으로 실측.

파이프라인:
  - 각 GT name_raw → parse_drug_item() → ParsedItem
  - exact_fast: dose 있고 DB ILIKE 단일 완전 매칭 → final_score = 1.0 (하드코딩)
  - rrf path: ILIKE retriever + Trigram retriever → fuse_rrf → DomainReranker
              → BgeRerankerAdapter (FlagEmbedding 설치된 경우만)
              → final_score = ranked[0].final_score
  - GT 매칭: top1 이름에 INN 키워드 포함 여부 (eval_full 동일 방식)

실행: python tests/eval/run_threshold_sweep.py
       (venv 활성화 필요: source .venv/bin/activate)

read-only: DB DELETE/UPDATE 없음.
"""
from __future__ import annotations

import asyncio
import json
import os
import re
from dataclasses import dataclass, field
from decimal import Decimal
from pathlib import Path

import asyncpg
import jamotools

from app.rag.ocr.parser import parse_drug_item
from app.rag.ocr.rrf import Candidate, fuse_rrf
from app.rag.ocr.reranker import DomainReranker, BgeRerankerAdapter

GT_JSONL = Path(__file__).parent / "gt" / "prescriptions.jsonl"
REPORT_DIR = Path(__file__).parent.parent.parent / "reports" / "eval"
POSTGRES_DSN = os.getenv(
    "POSTGRES_DSN",
    "postgresql://pillmate:pillmate_local@localhost:5433/pillmate",
)

THRESHOLDS = [0.50, 0.60, 0.70, 0.80]
ILIKE_TOP_N = 5
TRGM_TOP_N = 20

# ──────────────────────────────────────────────
# GT INN 매칭 (eval_full 동일 패턴)
# ──────────────────────────────────────────────
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
_UNIT_TOKENS = frozenset({"mg", "ml", "mcg", "ug", "g", "iu", "mg/ml"})


def _auto_inn(drug_name: str) -> str:
    m = re.match(r"^([가-힣]{2,})", drug_name)
    if m:
        return m.group(1)[:4]
    return drug_name[:4]


def _get_inn(gt_id: str, drug_name: str) -> str:
    if gt_id in _HARD_INN:
        return _HARD_INN[gt_id]
    if gt_id in _MEDIUM_INN:
        return _MEDIUM_INN[gt_id]
    return _auto_inn(drug_name)


def _is_hit(matched_name: str | None, inn: str) -> bool:
    if not matched_name or not inn:
        return False
    return inn in matched_name


# ──────────────────────────────────────────────
# DB 멀티 리트리버 (kd_code 를 item_seq 로 사용)
# ──────────────────────────────────────────────
_ILIKE_MULTI_SQL = """
SELECT kd_code, name, form, name_jamo
FROM drugs
WHERE status = 'ACTIVE'
  AND name ILIKE '%' || $1 || '%'
ORDER BY
  CASE WHEN name ILIKE $1 THEN 0
       WHEN name ILIKE $1 || '%' THEN 1
       ELSE 2 END,
  length(name)
LIMIT $2
"""

_TRGM_MULTI_SQL = """
SELECT kd_code, name, form, name_jamo,
       similarity(name, $1) AS trgm_score
FROM drugs
WHERE status = 'ACTIVE' AND name % $1
ORDER BY name <-> $1
LIMIT $2
"""


def _to_candidate(row: dict, alias_source: str | None = None) -> Candidate:
    name_jamo = row.get("name_jamo") or jamotools.split_syllables(row["name"])
    return Candidate(
        item_seq=row["kd_code"],
        name=row["name"],
        dose_amount=None,
        dose_unit=None,
        form=row.get("form"),
        alias_source=alias_source,
        name_jamo=name_jamo,
    )


class IlikeMultiRetriever:
    def __init__(self, pool: asyncpg.Pool, top_n: int = ILIKE_TOP_N) -> None:
        self._pool = pool
        self._top_n = top_n

    async def search(self, parsed) -> list[Candidate]:
        query = parsed.name or parsed.raw
        async with self._pool.acquire() as conn:
            rows = await conn.fetch(_ILIKE_MULTI_SQL, query, self._top_n)
        return [_to_candidate(dict(r)) for r in rows]


class TrgramMultiRetriever:
    def __init__(self, pool: asyncpg.Pool, top_n: int = TRGM_TOP_N) -> None:
        self._pool = pool
        self._top_n = top_n

    async def search(self, parsed) -> list[Candidate]:
        query = parsed.name or parsed.raw
        async with self._pool.acquire() as conn:
            rows = await conn.fetch(_TRGM_MULTI_SQL, query, self._top_n)
        return [_to_candidate(dict(r)) for r in rows]


_EXACT_SINGLE_SQL = """
SELECT kd_code, name, form, name_jamo
FROM drugs
WHERE status = 'ACTIVE'
  AND name ILIKE $1
ORDER BY length(name)
LIMIT 1
"""


class ExactSingleRetriever:
    def __init__(self, pool: asyncpg.Pool) -> None:
        self._pool = pool

    async def search_single(self, parsed) -> Candidate | None:
        async with self._pool.acquire() as conn:
            row = await conn.fetchrow(_EXACT_SINGLE_SQL, parsed.name)
        if row is None:
            return None
        return _to_candidate(dict(row))


# ──────────────────────────────────────────────
# 측정 결과 데이터클래스
# ──────────────────────────────────────────────
@dataclass
class SweepEntry:
    gt_id: str
    gt_kd_code: str
    name_raw: str
    difficulty: str
    inn: str
    stage: str
    top1_item_seq: str | None
    top1_name: str | None
    final_score: float
    matched: bool
    rrf_candidates_count: int


# ──────────────────────────────────────────────
# 메인 러너
# ──────────────────────────────────────────────
class ThresholdSweepRunner:
    def __init__(self, pool: asyncpg.Pool, use_bge: bool = True) -> None:
        self._pool = pool
        self._exact = ExactSingleRetriever(pool)
        self._retrievers = {
            "ilike": IlikeMultiRetriever(pool),
            "trgm": TrgramMultiRetriever(pool),
        }
        self._reranker = DomainReranker()
        self._bge: BgeRerankerAdapter | None = None
        if use_bge:
            try:
                self._bge = BgeRerankerAdapter()
                self._bge._load()
                print("[BGE] BAAI/bge-reranker-v2-m3 로드 성공")
            except Exception as e:
                print(f"[BGE] 로드 실패 — DomainReranker만 사용: {e}")
                self._bge = None

    async def run_one(self, entry: dict) -> SweepEntry:
        gt_id = entry["id"]
        gt_kd_code = entry["drugs"][0]["kd_code"]
        drug_name = entry["drugs"][0].get("name", entry["name_raw"])
        name_raw = entry["name_raw"]
        difficulty = entry.get("difficulty", "easy")
        inn = _get_inn(gt_id, drug_name)
        parsed = parse_drug_item(name_raw)

        # exact_fast path: dose 있고 단일 exact 매칭
        if parsed.dose_amount:
            fast = await self._exact.search_single(parsed)
            if fast is not None:
                matched = _is_hit(fast.name, inn)
                return SweepEntry(
                    gt_id=gt_id, gt_kd_code=gt_kd_code, name_raw=name_raw,
                    difficulty=difficulty, inn=inn,
                    stage="exact_fast", top1_item_seq=fast.item_seq,
                    top1_name=fast.name, final_score=1.0,
                    matched=matched, rrf_candidates_count=0,
                )

        # rrf path
        results = await asyncio.gather(
            *(r.search(parsed) for r in self._retrievers.values())
        )
        retriever_results = dict(zip(self._retrievers.keys(), results))
        fused = fuse_rrf(retriever_results)

        if not fused:
            return SweepEntry(
                gt_id=gt_id, gt_kd_code=gt_kd_code, name_raw=name_raw,
                difficulty=difficulty, inn=inn,
                stage="rrf_no_match", top1_item_seq=None,
                top1_name=None, final_score=0.0,
                matched=False, rrf_candidates_count=0,
            )

        ranked = self._reranker.rerank(parsed, fused[:30])
        if self._bge is not None:
            try:
                ranked = self._bge.rerank(parsed.raw, ranked)
            except Exception as e:
                if not getattr(self, "_bge_warned", False):
                    print(f"[BGE] rerank 실패 — DomainReranker only fallback: {e}")
                    self._bge_warned = True
                self._bge = None

        top1 = ranked[0]
        matched = _is_hit(top1.name, inn)
        return SweepEntry(
            gt_id=gt_id, gt_kd_code=gt_kd_code, name_raw=name_raw,
            difficulty=difficulty, inn=inn,
            stage="rrf", top1_item_seq=top1.item_seq,
            top1_name=top1.name, final_score=top1.final_score,
            matched=matched, rrf_candidates_count=len(fused),
        )

    async def run_all(self) -> list[SweepEntry]:
        entries = [
            json.loads(l) for l in GT_JSONL.read_text().splitlines() if l.strip()
        ]
        results = []
        for i, e in enumerate(entries):
            entry_result = await self.run_one(e)
            results.append(entry_result)
            if (i + 1) % 10 == 0:
                print(f"  [{i+1}/100] {e['id']} score={entry_result.final_score:.3f} match={entry_result.matched}")
        return results


# ──────────────────────────────────────────────
# 임계값 표 산출
# ──────────────────────────────────────────────
def compute_threshold_table(entries: list[SweepEntry], thresholds: list[float]) -> list[dict]:
    total = len(entries)
    rows = []
    for thr in thresholds:
        auto = [e for e in entries if e.final_score >= thr]
        review = [e for e in entries if e.final_score < thr]
        auto_correct = [e for e in auto if e.matched]
        auto_wrong = [e for e in auto if not e.matched]
        rows.append({
            "threshold": thr,
            "auto_count": len(auto),
            "auto_ratio": len(auto) / total,
            "auto_accuracy": len(auto_correct) / len(auto) if auto else 0.0,
            "review_count": len(review),
            "review_ratio": len(review) / total,
            "false_auto": len(auto_wrong),
            "false_auto_ids": [e.gt_id for e in auto_wrong],
        })
    return rows


def compute_score_distribution(entries: list[SweepEntry]) -> dict:
    exact = [e for e in entries if e.stage == "exact_fast"]
    rrf_only = [e for e in entries if e.stage == "rrf"]
    no_match = [e for e in entries if e.stage == "rrf_no_match"]

    def bucket(es: list[SweepEntry]) -> dict:
        if not es:
            return {}
        scores = [e.final_score for e in es]
        matched = sum(1 for e in es if e.matched)
        return {
            "count": len(es),
            "matched": matched,
            "hit_rate": matched / len(es),
            "min": min(scores),
            "max": max(scores),
            "mean": sum(scores) / len(scores),
            "p25": sorted(scores)[len(scores) // 4],
            "p50": sorted(scores)[len(scores) // 2],
            "p75": sorted(scores)[len(scores) * 3 // 4],
        }

    return {
        "exact_fast": bucket(exact),
        "rrf_only": bucket(rrf_only),
        "no_match": {
            "count": len(no_match),
            "ids": [e.gt_id for e in no_match],
        },
    }


# ──────────────────────────────────────────────
# 마크다운 리포트 생성
# ──────────────────────────────────────────────
def render_markdown(
    entries: list[SweepEntry],
    table_rows: list[dict],
    dist: dict,
    use_bge: bool,
) -> str:
    total = len(entries)
    overall_matched = sum(1 for e in entries if e.matched)
    bge_label = "DomainReranker + BgeReranker(BAAI/bge-reranker-v2-m3)" if use_bge else "DomainReranker only (BGE 미설치)"

    lines = [
        "# Threshold Sweep Report — 2026-06-13",
        "",
        f"**GT 건수**: {total}  |  **파이프라인**: {bge_label}",
        f"**전체 Hit@1**: {overall_matched}/{total} = {overall_matched/total:.3f}",
        "",
        "> ⚠️ **측정 한계 (포폴 정직성)**: RrfMatcher는 현재 실서비스(DrugMatcher cascade)에 연결되지 않음.",
        "> 이 스크립트는 `drugs.kd_code`를 `item_seq`로 사용한 simplified wiring으로 측정.",
        "> exact_fast final_score=1.0은 하드코딩이며 RRF 경로 점수 분포와 분리 집계.",
        "",
        "## 1. Stage 분포",
        "",
        f"| stage | count | hit_rate | score_mean |",
        "|-------|-------|----------|------------|",
    ]

    ef = dist["exact_fast"]
    if ef:
        lines.append(f"| exact_fast | {ef['count']} | {ef['hit_rate']:.3f} | {ef['mean']:.3f} (1.0 고정) |")
    rrf = dist["rrf_only"]
    if rrf:
        lines.append(f"| rrf | {rrf['count']} | {rrf['hit_rate']:.3f} | {rrf['mean']:.3f} |")
    nm = dist["no_match"]
    if nm["count"]:
        lines.append(f"| rrf_no_match | {nm['count']} | 0.000 | 0.000 |")

    lines += [
        "",
        "## 2. RRF-only 점수 분포 (exact_fast 제외)",
        "",
        f"| 지표 | 값 |",
        "|------|-----|",
    ]
    if rrf:
        lines += [
            f"| count | {rrf['count']} |",
            f"| hit_rate | {rrf['hit_rate']:.3f} |",
            f"| min | {rrf['min']:.4f} |",
            f"| p25 | {rrf['p25']:.4f} |",
            f"| p50 (중앙값) | {rrf['p50']:.4f} |",
            f"| p75 | {rrf['p75']:.4f} |",
            f"| max | {rrf['max']:.4f} |",
        ]

    lines += [
        "",
        "## 3. 임계값별 자동확정 품질 (전체 100건 기준)",
        "",
        "> **false_auto**: 자동확정 대상 중 오답 — 의료 안전상 가장 중요한 지표.",
        "",
        "| threshold | auto_count | auto_ratio | auto_accuracy | false_auto | review_ratio |",
        "|-----------|-----------|------------|---------------|------------|--------------|",
    ]
    for r in table_rows:
        lines.append(
            f"| {r['threshold']:.2f} | {r['auto_count']} | {r['auto_ratio']:.2%} "
            f"| {r['auto_accuracy']:.3f} | {r['false_auto']} ({', '.join(r['false_auto_ids']) or '-'}) "
            f"| {r['review_ratio']:.2%} |"
        )

    # Current threshold row highlight
    current_row = next((r for r in table_rows if abs(r["threshold"] - 0.70) < 0.01), None)
    if current_row:
        lines += [
            "",
            f"> **현재 운영값 0.70**: auto {current_row['auto_count']}건({current_row['auto_ratio']:.1%}), "
            f"정확도 {current_row['auto_accuracy']:.1%}, false_auto {current_row['false_auto']}건",
        ]

    # Score histogram for RRF-only (dynamic range — scores may be negative without BGE)
    if rrf:
        rrf_entries = [e for e in entries if e.stage == "rrf"]
        scores = [e.final_score for e in rrf_entries]
        s_min = min(scores)
        s_max = max(scores)
        s_range = s_max - s_min if s_max > s_min else 1.0
        NUM_BUCKETS = 10
        buckets: list[int] = [0] * NUM_BUCKETS
        for s in scores:
            idx = min(int((s - s_min) / s_range * NUM_BUCKETS), NUM_BUCKETS - 1)
            buckets[idx] += 1
        lines += [
            "",
            "## 4. RRF-only 점수 히스토그램 (실제 점수 범위 기준)",
            "",
            f"> ⚠️ BGE(normalize=True) 미적용 시 점수는 0-1 범위가 아님. 실제 범위: [{s_min:.4f}, {s_max:.4f}]",
            "> ABS_THRESHOLD=0.70은 BGE normalize path 기준 — RRF+DomainReranker only 점수와 직접 비교 불가.",
            "",
            "| 구간 | count | bar |",
            "|------|-------|-----|",
        ]
        for i, cnt in enumerate(buckets):
            lo = s_min + s_range * i / NUM_BUCKETS
            hi = s_min + s_range * (i + 1) / NUM_BUCKETS
            bar = "█" * cnt
            lines.append(f"| {lo:.3f}–{hi:.3f} | {cnt} | {bar} |")

    # Miss list
    misses = [e for e in entries if not e.matched]
    lines += [
        "",
        f"## 5. 미매칭 ({len(misses)}건)",
        "",
        "| gt_id | difficulty | name_raw | top1_name | score |",
        "|-------|-----------|----------|-----------|-------|",
    ]
    for e in sorted(misses, key=lambda x: (x.difficulty, x.gt_id)):
        lines.append(
            f"| {e.gt_id} | {e.difficulty} | {e.name_raw} | {e.top1_name or '(없음)'} | {e.final_score:.4f} |"
        )

    # 분석 요약 — BGE 미작동 시 별도 메시지
    if not use_bge:
        rrf_only_entries = [e for e in entries if e.stage == "rrf"]
        rrf_hit = sum(1 for e in rrf_only_entries if e.matched)
        exact_entries = [e for e in entries if e.stage == "exact_fast"]
        exact_hit = sum(1 for e in exact_entries if e.matched)
        exact_label = (
            f"{len(exact_entries)}건 중 {exact_hit}건 정답 "
            f"({exact_hit/len(exact_entries):.1%} — 이 경로만 AUTO 가능)"
            if exact_entries else "0건 (GT 파싱 후 exact name match 없음 — 함량 포함 이름 불일치)"
        )
        rrf_hit_label = (
            f"{rrf_hit}/{len(rrf_only_entries)} = {rrf_hit/len(rrf_only_entries):.3f}"
            if rrf_only_entries else "0/0"
        )
        opinion_lines = [
            "## 6. 분석 요약 — BGE 미작동 상황",
            "",
            "**⚠️ 핵심 발견**: `BgeRerankerAdapter.rerank()` 가 `XLMRobertaTokenizer.prepare_for_model` 누락으로 실패.",
            "transformers ≥ 4.47 에서 해당 메서드가 제거됨. FlagEmbedding 버전 고정 필요.",
            "",
            "**ABS_THRESHOLD=0.70 의 실제 상태**:",
            f"- exact_fast path (final_score=1.0 하드코딩): {exact_label}",
            "- rrf+DomainReranker path: BGE normalize 없이 점수 범위 ≈ [-1, 0.1] → **전량 MANUAL 처리**",
            "- 결론: 현재 환경에서 ABS_THRESHOLD=0.70은 **미튜닝 상태**. BGE 의존성 수정 후 재측정 필요.",
            "",
            f"**RRF+DomainReranker Hit@1 (임계값 무관)**: {rrf_hit_label}",
            "→ BGE 수정 후 이 비율이 AUTO 확정 풀의 상한선이 됨.",
        ]
        lines += [""] + opinion_lines
    else:
        current_row = next((r for r in table_rows if abs(r["threshold"] - 0.70) < 0.01), None)
        if current_row:
            if current_row["false_auto"] == 0:
                opinion = (
                    f"**의견**: 0.70은 false_auto=0으로 의료 안전 충족. "
                    f"단, auto_ratio={current_row['auto_ratio']:.1%}로 낮으면 "
                    f"임계값 하향(0.60) 검토 가능 — 해당 구간 false_auto 확인 필수."
                )
            elif current_row["false_auto"] <= 2:
                opinion = (
                    f"**의견**: 0.70은 false_auto={current_row['false_auto']}건으로 경계선. "
                    f"0.75–0.80 상향이 의료 안전에 유리. 면접 서술 시 솔직하게 언급 권고."
                )
            else:
                opinion = (
                    f"**의견**: 0.70에서 false_auto={current_row['false_auto']}건 — "
                    f"데이터 미튜닝 상태임. 임계값 상향(0.80)이 필요하거나 "
                    f"BgeReranker 도입 후 재측정 권고."
                )
            lines += ["", "## 6. 분석 요약", "", opinion]

    return "\n".join(lines) + "\n"


async def main() -> None:
    print(f"[SWEEP] GT 100건 — 임계값 sweep 시작")
    print(f"[SWEEP] DB: {POSTGRES_DSN}")

    pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=2, max_size=4)
    try:
        runner = ThresholdSweepRunner(pool, use_bge=True)
        entries = await runner.run_all()
    finally:
        await pool.close()

    table_rows = compute_threshold_table(entries, THRESHOLDS)
    dist = compute_score_distribution(entries)
    use_bge = (runner._bge is not None) and not getattr(runner, "_bge_warned", False)

    print("\n[SWEEP] 임계값 표:")
    print(f"{'thr':>6} | {'auto':>4} | {'auto_acc':>8} | {'false':>5} | {'review%':>7}")
    print("-" * 50)
    for r in table_rows:
        print(
            f"{r['threshold']:>6.2f} | {r['auto_count']:>4} | {r['auto_accuracy']:>8.3f} | "
            f"{r['false_auto']:>5} | {r['review_ratio']:>7.2%}"
        )

    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    out_md = REPORT_DIR / "threshold_sweep_2026-06-13.md"
    md = render_markdown(entries, table_rows, dist, use_bge)
    out_md.write_text(md, encoding="utf-8")
    print(f"\n[SWEEP] 리포트 저장: {out_md}")

    # JSON raw data
    out_json = REPORT_DIR / "threshold_sweep_2026-06-13.json"
    raw_data = {
        "total": len(entries),
        "use_bge": use_bge,
        "threshold_table": table_rows,
        "dist": dist,
        "entries": [
            {
                "gt_id": e.gt_id, "gt_kd_code": e.gt_kd_code, "name_raw": e.name_raw,
                "difficulty": e.difficulty, "stage": e.stage, "top1_name": e.top1_name,
                "final_score": e.final_score, "matched": e.matched,
            }
            for e in entries
        ],
    }
    out_json.write_text(json.dumps(raw_data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[SWEEP] JSON 저장: {out_json}")


if __name__ == "__main__":
    asyncio.run(main())
