"""
실 처방전 8장 — 운영 RRF 경로(build_rrf_matcher_inner) E2E 정성 평가

run_real_e2e.py 의 _cascade_search(평가 전용 레거시) 를 Gate D 에서 통합한
운영 RRF 경로로 교체. 이 파일이 #151 마감 정직성 기준 실측값.

실행:
  cd back/ai_server
  POSTGRES_DSN=... GEMINI_API_KEY=... uv run python tests/eval/run_real_e2e_rrf.py

cost-aware: Gemini Flash, 8장 < $0.05
medical-safety: OCR 프롬프트가 환자 식별 정보 마스킹 강제
"""
from __future__ import annotations

import asyncio
import base64
import json
import os
import re
from dataclasses import dataclass, field
from pathlib import Path

import asyncpg
from langchain_core.messages import HumanMessage
from langchain_core.output_parsers import PydanticOutputParser
from langchain_google_genai import ChatGoogleGenerativeAI

from app.domain.ocr import RawOcrItem, RawOcrItemList
from app.rag.ocr.matcher import MatchResult
from app.rag.ocr.normalizer import normalize_for_cascade
from app.rag.ocr.parser import parse_drug_item
from app.rag.ocr.rrf import MatchDecisionType
from app.rag.ocr.rrf_factory import build_rrf_matcher_inner

REAL_IMG_DIR = Path(__file__).parent / "real_prescriptions"
REPORT_DIR = Path(__file__).parent.parent.parent / "reports" / "eval"
PROMPT_PATH = (
    Path(__file__).parent.parent.parent
    / "app"
    / "rag"
    / "prompts"
    / "ocr_system.txt"
)
POSTGRES_DSN = os.getenv(
    "POSTGRES_DSN",
    "postgresql://pillmate:pillmate_local@localhost:5433/pillmate",
)
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = "gemini-2.5-flash"

_MIME_MAP = {
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".webp": "image/webp",
}


def _mime_type(path: Path) -> str:
    return _MIME_MAP.get(path.suffix.lower(), "image/jpeg")


def _top_candidate_name(result: MatchResult) -> str | None:
    """AUTO/CONFIRM → primary.name. MANUAL → options[0].name."""
    if result.decision is None:
        return None
    if result.decision.primary is not None:
        return result.decision.primary.name
    if result.decision.options:
        return result.decision.options[0].name
    return None


def _decision_type_str(result: MatchResult) -> str:
    if result.decision is None:
        return "NONE"
    return result.decision.type.value if hasattr(result.decision.type, "value") else str(result.decision.type)


def _is_suspicious_auto(name_raw: str, matched_name: str | None, result: MatchResult) -> bool:
    """AUTO 결정 중 false-auto 의심 케이스 탐지.

    휴리스틱:
    - exact_fast 경로: StrongExact 단일 매치 → final_score=0.0 이 정상, 점수 기반 의심 제외
    - rrf 경로 AUTO: final_score < 0.80 이면 의심
    - 이름 기반: 쿼리 앞 3자 한글이 매칭명에 없으면 의심 (prefix mismatch)
    - 함량 기반: 쿼리/매칭명 양쪽에 mg/밀리그램 있고 수치가 다르면 의심
    """
    if result.decision is None or result.decision.type != MatchDecisionType.AUTO:
        return False
    if matched_name is None:
        return True

    # rrf 경로 LOW score 의심 (exact_fast는 score=0.0이 정상이므로 제외)
    if result.stage != "exact_fast":
        score = result.decision.primary.final_score if result.decision.primary else 0.0
        if score < 0.80:
            return True

    # 이름 기반: 쿼리 앞 3자 한글 prefix 가 매칭명에 없으면 의심
    kor_prefix = re.match(r"^([가-힣]{2,})", name_raw)
    if kor_prefix:
        prefix = kor_prefix.group(1)[:3]
        if prefix not in matched_name:
            return True

    # 함량 불일치: 쿼리와 매칭명 양쪽 모두 mg/밀리그램 수치가 있고 다르면 의심
    dose_q = re.search(r"(\d+(?:\.\d+)?)\s*(?:mg|밀리그램)", name_raw, re.IGNORECASE)
    dose_m = re.search(r"(\d+(?:\.\d+)?)\s*(?:mg|밀리그램)", matched_name, re.IGNORECASE)
    if dose_q and dose_m and float(dose_q.group(1)) != float(dose_m.group(1)):
        return True

    return False


@dataclass
class DrugMatchRow:
    name_raw: str
    confidence: float
    matched_name: str | None
    decision: str
    final_score: float
    stage: str
    suspicious_auto: bool = False


@dataclass
class ImageResult:
    image_name: str
    items: list[RawOcrItem] = field(default_factory=list)
    matches: list[DrugMatchRow] = field(default_factory=list)
    error: str | None = None


class RealE2ERrfRunner:
    """실 약봉투 이미지 → Gemini OCR → 운영 RrfMatcher 전체 흐름."""

    def __init__(self, pool: asyncpg.Pool, api_key: str) -> None:
        self._pool = pool
        self._matcher = build_rrf_matcher_inner(pool)
        parser = PydanticOutputParser(pydantic_object=RawOcrItemList)
        raw_prompt = PROMPT_PATH.read_text(encoding="utf-8")
        self._prompt = raw_prompt.replace(
            "{format_instructions}", parser.get_format_instructions()
        )
        self._parser = parser
        self._llm = ChatGoogleGenerativeAI(
            model=GEMINI_MODEL,
            google_api_key=api_key,
            temperature=0.0,
        )

    async def _vision_extract(self, image_path: Path) -> tuple[list[RawOcrItem], str | None]:
        image_bytes = image_path.read_bytes()
        mime = _mime_type(image_path)
        encoded = base64.b64encode(image_bytes).decode("ascii")
        message = HumanMessage(
            content=[
                {"type": "text", "text": self._prompt},
                {"type": "image_url", "image_url": f"data:{mime};base64,{encoded}"},
            ]
        )
        try:
            result = await self._llm.ainvoke([message])
            raw = getattr(result, "content", str(result))
        except Exception as exc:
            return [], f"vision_error: {exc.__class__.__name__}: {exc}"

        try:
            parsed = self._parser.parse(raw)
            return parsed.items, None
        except Exception as exc:
            return [], f"parse_error: {exc.__class__.__name__}"

    async def _match_item(self, item: RawOcrItem) -> DrugMatchRow:
        parsed = parse_drug_item(normalize_for_cascade(item.name_raw))
        result = await self._matcher.match(parsed)
        matched_name = _top_candidate_name(result)
        decision = _decision_type_str(result)
        score = (
            result.decision.primary.final_score
            if result.decision and result.decision.primary
            else 0.0
        )
        suspicious = _is_suspicious_auto(item.name_raw, matched_name, result)
        return DrugMatchRow(
            name_raw=item.name_raw,
            confidence=float(item.confidence),
            matched_name=matched_name,
            decision=decision,
            final_score=score,
            stage=result.stage,
            suspicious_auto=suspicious,
        )

    async def run_image(self, image_path: Path) -> ImageResult:
        items, error = await self._vision_extract(image_path)
        if error:
            return ImageResult(image_name=image_path.name, error=error)

        matches = []
        for item in items:
            row = await self._match_item(item)
            matches.append(row)

        return ImageResult(image_name=image_path.name, items=items, matches=matches)

    async def run_all(self) -> list[ImageResult]:
        images = sorted(
            p for p in REAL_IMG_DIR.glob("*") if p.suffix.lower() in _MIME_MAP
        )
        results = []
        for img in images:
            print(f"  처리 중: {img.name} ...", flush=True)
            r = await self.run_image(img)
            results.append(r)
        return results


def _build_md_report(results: list[ImageResult]) -> str:
    lines: list[str] = []
    lines.append("# Real E2E RRF 평가 — 2026-06-13")
    lines.append("")
    lines.append("> 운영 경로: `build_rrf_matcher_inner` (Gate D 통합, #150)")
    lines.append("> 레거시 `_cascade_search` 완전 제거 후 첫 실 처방전 측정.")
    lines.append("")

    # 요약 표
    lines.append("## 이미지별 요약")
    lines.append("")
    lines.append("| 이미지 | 추출 | AUTO | CONFIRM | MANUAL | 실패 | 의심 오매칭 |")
    lines.append("|--------|------|------|---------|--------|------|-------------|")

    total_extracted = 0
    total_auto = 0
    total_confirm = 0
    total_manual = 0
    total_fail = 0
    total_suspicious = 0

    for r in results:
        if r.error:
            lines.append(f"| {r.image_name} | — | — | — | — | ERROR | — |")
            total_fail += 1
            continue

        auto = sum(1 for m in r.matches if m.decision == "AUTO")
        confirm = sum(1 for m in r.matches if m.decision == "CONFIRM")
        manual = sum(1 for m in r.matches if m.decision == "MANUAL")
        fail = sum(1 for m in r.matches if m.matched_name is None)
        suspicious = sum(1 for m in r.matches if m.suspicious_auto)

        total_extracted += len(r.matches)
        total_auto += auto
        total_confirm += confirm
        total_manual += manual
        total_fail += fail
        total_suspicious += suspicious

        lines.append(
            f"| {r.image_name} | {len(r.matches)} | {auto} | {confirm} | {manual} | {fail} | {suspicious} |"
        )

    lines.append(
        f"| **합계** | **{total_extracted}** | **{total_auto}** | **{total_confirm}** | **{total_manual}** | **{total_fail}** | **{total_suspicious}** |"
    )
    lines.append("")

    if total_extracted > 0:
        auto_rate = total_auto / total_extracted
        surfacing_rate = (total_auto + total_confirm + total_manual) / total_extracted
        lines.append(f"- 전체 추출 약품: {total_extracted}개")
        lines.append(f"- AUTO 확정률: {total_auto}/{total_extracted} = {auto_rate:.1%}")
        lines.append(f"- surfacing (AUTO+CONFIRM+MANUAL 후보 제시): {surfacing_rate:.1%}")
        lines.append(f"- 의심 오매칭(false-auto 후보): {total_suspicious}건")
        lines.append("")

    # 이미지별 상세
    lines.append("## 이미지별 상세")
    lines.append("")

    for r in results:
        lines.append(f"### {r.image_name}")
        lines.append("")
        if r.error:
            lines.append(f"**ERROR**: `{r.error}`")
            lines.append("")
            continue

        lines.append("| 추출명 | OCR신뢰 | 매칭명 | 결정 | 점수 | stage | ⚠️ |")
        lines.append("|--------|---------|--------|------|------|-------|-----|")
        for m in r.matches:
            suspicious_mark = "⚠️" if m.suspicious_auto else ""
            matched_display = m.matched_name or "—"
            lines.append(
                f"| `{m.name_raw}` | {m.confidence:.2f} | {matched_display} "
                f"| {m.decision} | {m.final_score:.3f} | {m.stage} | {suspicious_mark} |"
            )
        lines.append("")

    return "\n".join(lines)


async def main() -> None:
    if not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY 환경변수 미설정")

    print("DB 연결 중 ...", flush=True)
    pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=4)
    try:
        print("RrfMatcher 초기화 (BGE 포함) ...", flush=True)
        runner = RealE2ERrfRunner(pool, GEMINI_API_KEY)

        print("8장 처리 시작 ...", flush=True)
        results = await runner.run_all()
    finally:
        await pool.close()

    # 보고서 저장
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    md = _build_md_report(results)
    report_path = REPORT_DIR / "real_e2e_rrf_2026-06-13.md"
    report_path.write_text(md, encoding="utf-8")
    print(f"\n보고서 저장: {report_path}")

    # JSON 저장
    json_path = REPORT_DIR / "real_e2e_rrf_2026-06-13.json"
    json_data = []
    for r in results:
        json_data.append({
            "image": r.image_name,
            "error": r.error,
            "drug_count": len(r.matches),
            "auto_count": sum(1 for m in r.matches if m.decision == "AUTO"),
            "confirm_count": sum(1 for m in r.matches if m.decision == "CONFIRM"),
            "manual_count": sum(1 for m in r.matches if m.decision == "MANUAL"),
            "suspicious_auto_count": sum(1 for m in r.matches if m.suspicious_auto),
            "drugs": [
                {
                    "name_raw": m.name_raw,
                    "confidence": m.confidence,
                    "matched_name": m.matched_name,
                    "decision": m.decision,
                    "final_score": m.final_score,
                    "stage": m.stage,
                    "suspicious_auto": m.suspicious_auto,
                }
                for m in r.matches
            ],
        })
    json_path.write_text(json.dumps(json_data, ensure_ascii=False, indent=2))
    print(f"JSON 저장: {json_path}")

    # 콘솔 요약
    print("\n" + "=" * 60)
    print(md)


if __name__ == "__main__":
    asyncio.run(main())
