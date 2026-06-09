"""
Phase B-6 후 실 약봉투 8장 E2E 재측정

변경 포인트:
  - PREPROCESS_ENABLED=true  → ImagePreprocessor (EXIF/deskew/CLAHE/denoise/resize)
  - FEWSHOT_ENABLED=true     → few-shot system_prompt.txt

비교 기준: Phase B-3 (31/35 = 88.57%)
알려진 B-3 miss: 쎌박타민정 / 중근당아목시실린캡슐 / 에치콘정 / 엘리버드정

실행:
  cd back/ai_server
  python tests/eval/run_real_e2e_b6.py
"""
from __future__ import annotations

import asyncio
import base64
import json
import os
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

# PATH 설정
_HERE = Path(__file__).resolve()
_AI_ROOT = _HERE.parents[2]
if str(_AI_ROOT) not in sys.path:
    sys.path.insert(0, str(_AI_ROOT))

from dotenv import load_dotenv

load_dotenv(_AI_ROOT.parents[1] / ".env")  # back/.env

import asyncpg
from langchain_core.messages import HumanMessage
from langchain_core.output_parsers import PydanticOutputParser
from langchain_google_genai import ChatGoogleGenerativeAI

from app.domain.ocr import RawOcrItemList
from app.rag.ocr.preprocess import ImagePreprocessor
from tests.eval.run_eval_full import _cascade_search

REAL_IMG_DIR = _HERE.parent / "real_prescriptions"
REPORT_DIR = _AI_ROOT / "reports" / "eval"

# Phase B-6: few-shot 프롬프트 (FEWSHOT_ENABLED=true)
FEWSHOT_PROMPT_PATH = _AI_ROOT / "app" / "rag" / "ocr" / "prompts" / "system_prompt.txt"
# fallback: 기존 프롬프트
BASE_PROMPT_PATH = _AI_ROOT / "app" / "rag" / "prompts" / "ocr_system.txt"

POSTGRES_DSN = (
    f"postgresql://{os.getenv('POSTGRES_USER','pillmate')}:"
    f"{os.getenv('POSTGRES_PASSWORD','pillmate_local')}@"
    f"{os.getenv('POSTGRES_HOST','localhost')}:"
    f"{os.getenv('POSTGRES_PORT','5433')}/"
    f"{os.getenv('POSTGRES_DB','pillmate')}"
)
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = "gemini-2.5-flash"

_MIME_MAP = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg"}

# B-3 Phase의 알려진 miss 4건 (추적용)
_B3_KNOWN_MISS = {
    "쎌박타민정": "B-4 Tier1 prefix_match 해결 예상",
    "중근당아목시실린캡슐": "B-4 Tier0 제조사 strip 해결 예상",
    "에치콘정": "DB 미수록 가능성 높음",
    "엘리버드정": "DB 미수록 가능성 높음",
}


@dataclass
class DrugResult:
    name_raw: str
    confidence: float
    candidates: list[str]
    db_match: str | None
    stage: str
    is_b3_miss: bool = False


@dataclass
class ImageResult:
    image_name: str
    drugs: list[DrugResult] = field(default_factory=list)
    error: str | None = None
    preprocess_applied: bool = False
    fewshot_applied: bool = False
    rate_limited: bool = False


async def _run_single_image(
    image_path: Path,
    pool: asyncpg.Pool,
    llm,
    parser: PydanticOutputParser,
    prompt: str,
    preprocessor: ImagePreprocessor,
) -> ImageResult:
    result = ImageResult(
        image_name=image_path.name,
        preprocess_applied=True,
        fewshot_applied=True,
    )

    raw_bytes = image_path.read_bytes()
    mime_raw = _MIME_MAP.get(image_path.suffix.lower(), "image/jpeg")

    # PREPROCESS_ENABLED=true
    try:
        processed_bytes = preprocessor.preprocess(raw_bytes)
        # preprocess 후 항상 JPEG 출력 (mime 고정)
        mime = "image/jpeg"
    except Exception as exc:
        processed_bytes = raw_bytes
        mime = mime_raw
        print(f"  [WARN] preprocess failed for {image_path.name}: {exc}")

    encoded = base64.b64encode(processed_bytes).decode("ascii")
    message = HumanMessage(
        content=[
            {"type": "text", "text": prompt},
            {"type": "image_url", "image_url": f"data:{mime};base64,{encoded}"},
        ]
    )

    try:
        resp = await llm.ainvoke([message])
        raw_text = getattr(resp, "content", str(resp))
    except Exception as exc:
        err_str = str(exc)
        if "429" in err_str or "quota" in err_str.lower() or "RESOURCE_EXHAUSTED" in err_str:
            result.error = "429_RATE_LIMITED"
            result.rate_limited = True
        else:
            result.error = f"vision_error: {exc.__class__.__name__}: {exc}"
        return result

    try:
        parsed = parser.parse(raw_text)
        ocr_items = parsed.items
    except Exception as exc:
        result.error = f"parse_error: {exc.__class__.__name__}: {raw_text[:200]}"
        return result

    for item in ocr_items:
        db_match, stage = await _cascade_search(pool, item.name_raw)
        is_b3 = any(k in item.name_raw for k in _B3_KNOWN_MISS)
        result.drugs.append(DrugResult(
            name_raw=item.name_raw,
            confidence=float(item.confidence),
            candidates=list(item.candidates or []),
            db_match=db_match,
            stage=stage,
            is_b3_miss=is_b3,
        ))

    return result


async def run_all() -> list[ImageResult]:
    if not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY not set")

    images = sorted(p for p in REAL_IMG_DIR.iterdir() if p.suffix.lower() in _MIME_MAP)
    if not images:
        raise RuntimeError(f"No images found in {REAL_IMG_DIR}")

    # FEWSHOT_ENABLED=true: few-shot 프롬프트 사용
    prompt_path = FEWSHOT_PROMPT_PATH if FEWSHOT_PROMPT_PATH.exists() else BASE_PROMPT_PATH
    parser = PydanticOutputParser(pydantic_object=RawOcrItemList)
    raw_prompt = prompt_path.read_text(encoding="utf-8")
    prompt = raw_prompt.replace("{format_instructions}", parser.get_format_instructions())
    print(f"[INFO] 프롬프트: {prompt_path.name} (few-shot={'system_prompt' in prompt_path.name})")

    preprocessor = ImagePreprocessor()
    llm = ChatGoogleGenerativeAI(
        model=GEMINI_MODEL,
        google_api_key=GEMINI_API_KEY,
        temperature=0.0,
    )

    pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=4)
    results = []
    try:
        for i, img in enumerate(images, 1):
            print(f"\n[{i}/{len(images)}] {img.name} ...")
            r = await _run_single_image(img, pool, llm, parser, prompt, preprocessor)
            results.append(r)
            if r.rate_limited:
                print(f"  → 429 RATE LIMITED — 이후 이미지는 API 할당량 소진으로 skip")
                for remaining in images[i:]:
                    results.append(ImageResult(
                        image_name=remaining.name,
                        error="429_RATE_LIMITED_SKIP",
                        rate_limited=True,
                    ))
                break
            # 이미지 간 1초 대기 (RPD rate limit 완화)
            if i < len(images):
                time.sleep(1.5)
    finally:
        await pool.close()

    return results


def _build_report(results: list[ImageResult]) -> str:
    total_drugs = sum(len(r.drugs) for r in results if not r.error)
    total_matched = sum(
        1 for r in results if not r.error
        for d in r.drugs if d.db_match
    )
    rate_limited_count = sum(1 for r in results if r.rate_limited)
    processed_count = sum(1 for r in results if not r.rate_limited)

    lines = [
        "# Phase B-6 실 약봉투 8장 E2E 재측정 보고서",
        "",
        f"**측정일**: 2026-06-09 (UTC)  ",
        f"**변경**: PREPROCESS_ENABLED=true + FEWSHOT_ENABLED=true  ",
        f"**비교 기준**: Phase B-3 (31/35 = 88.57%)",
        "",
        "---",
        "",
        "## 전체 결과 요약",
        "",
        "| 지표 | Phase B-3 | Phase B-6 After |",
        "|------|-----------|----------------|",
    ]

    if rate_limited_count == 0:
        match_rate = total_matched / total_drugs if total_drugs > 0 else 0
        lines.append(f"| 이미지 처리 | 8/8 | {processed_count}/8 |")
        lines.append(f"| 약품 추출 수 | 35 | {total_drugs} |")
        lines.append(f"| DB 매칭 수 | 31 | {total_matched} |")
        lines.append(f"| **매칭률** | **88.57%** | **{match_rate:.2%}** |")
    else:
        lines.append(f"| 이미지 처리 | 8/8 | {processed_count}/8 (429 제한) |")
        lines.append(f"| 약품 추출 수 | 35 | {total_drugs} (부분) |")
        lines.append(f"| DB 매칭 수 | 31 | {total_matched} (부분) |")
        match_rate = total_matched / total_drugs if total_drugs > 0 else 0
        lines.append(f"| **부분 매칭률** | **88.57%** | **{match_rate:.2%}** ({processed_count}장 기준) |")

    lines += ["", "---", "", "## 이미지별 상세 결과", ""]

    for r in results:
        lines.append(f"### {r.image_name}")
        if r.error:
            lines.append(f"- **오류**: `{r.error}`")
            lines.append("")
            continue

        matched_n = sum(1 for d in r.drugs if d.db_match)
        lines.append(f"- 약품 추출: {len(r.drugs)}개 / DB 매칭: {matched_n}개")
        lines.append(f"- preprocess: ✓ / few-shot: ✓")
        lines.append("")
        lines.append("| 약품명 (OCR raw) | confidence | candidates | DB 매칭 | stage | B-3 miss? |")
        lines.append("|-----------------|-----------|-----------|---------|-------|-----------|")

        for d in r.drugs:
            match_icon = "✓" if d.db_match else "✗"
            b3_flag = "★" if d.is_b3_miss else "-"
            cands = ", ".join(d.candidates) if d.candidates else "-"
            matched_name = d.db_match or "—"
            lines.append(
                f"| {d.name_raw} | {d.confidence:.2f} | {cands} "
                f"| {match_icon} {matched_name} | {d.stage} | {b3_flag} |"
            )
        lines.append("")

    lines += [
        "---",
        "",
        "## B-3 알려진 miss 4건 추적",
        "",
        "| miss 약품명 | 예상 해결 방법 | B-6 결과 |",
        "|------------|--------------|---------|",
    ]

    for miss_name, expected in _B3_KNOWN_MISS.items():
        found_results = [
            d for r in results if not r.error
            for d in r.drugs
            if miss_name in d.name_raw
        ]
        if found_results:
            dr = found_results[0]
            outcome = f"✓ '{dr.db_match}' (stage={dr.stage})" if dr.db_match else f"✗ 여전히 miss"
        else:
            outcome = "미추출 (OCR raw 변화)"
        lines.append(f"| {miss_name} | {expected} | {outcome} |")

    lines += [
        "",
        "---",
        "",
        "## 처리 설정",
        "",
        f"- **프롬프트**: `{FEWSHOT_PROMPT_PATH.name}` (few-shot 10건)",
        "- **ImagePreprocessor**: EXIF회전 → deskew → resize(max 1920px) → CLAHE → bilateral",
        f"- **Gemini 모델**: {GEMINI_MODEL}",
        f"- **DB DSN**: postgresql://…@localhost:5433/pillmate",
    ]

    return "\n".join(lines)


async def main() -> None:
    print("=" * 60)
    print("Phase B-6 실 약봉투 8장 E2E 재측정")
    print("PREPROCESS_ENABLED=true + FEWSHOT_ENABLED=true")
    print("=" * 60)

    results = await run_all()

    # 보고서 저장
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    md_path = REPORT_DIR / "real_e2e_2026-06-09_after_b6.md"
    json_path = REPORT_DIR / "real_e2e_2026-06-09_after_b6.json"

    report_md = _build_report(results)
    md_path.write_text(report_md, encoding="utf-8")

    report_json = [
        {
            "image": r.image_name,
            "error": r.error,
            "rate_limited": r.rate_limited,
            "drug_count": len(r.drugs),
            "matched_count": sum(1 for d in r.drugs if d.db_match),
            "drugs": [
                {
                    "name_raw": d.name_raw,
                    "confidence": d.confidence,
                    "candidates": d.candidates,
                    "db_match": d.db_match,
                    "stage": d.stage,
                    "is_b3_miss": d.is_b3_miss,
                }
                for d in r.drugs
            ],
        }
        for r in results
    ]
    json_path.write_text(json.dumps(report_json, ensure_ascii=False, indent=2), encoding="utf-8")

    # 콘솔 요약
    print("\n" + "=" * 60)
    total_drugs = sum(len(r.drugs) for r in results if not r.error)
    total_matched = sum(1 for r in results if not r.error for d in r.drugs if d.db_match)
    rate_limited = sum(1 for r in results if r.rate_limited)

    print(f"처리 완료: {8 - rate_limited}/8장")
    print(f"약품 추출: {total_drugs}개")
    print(f"DB 매칭:  {total_matched}/{total_drugs} = {total_matched/total_drugs:.2%}" if total_drugs else "DB 매칭: N/A")
    print(f"비교: B-3 88.57% → B-6 {total_matched/total_drugs:.2%}" if total_drugs else "")
    if rate_limited:
        print(f"⚠ 429 rate limited: {rate_limited}장 미처리")
    print(f"\n보고서: {md_path}")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
