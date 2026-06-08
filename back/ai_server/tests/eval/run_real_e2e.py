"""
실 약봉투 8장 Gemini Flash Vision E2E 평가

실행: pytest -m eval_full tests/eval/run_real_e2e.py -v -s

요구 사항:
  - GEMINI_API_KEY 환경변수 필요
  - DB 연결 (POSTGRES_DSN) 필요
  - cost-aware: Gemini Flash만 사용, 8장 < $0.05
  - medical-safety: OCR 시스템 프롬프트가 환자 식별 정보 마스킹 강제

이미지 위치: tests/eval/real_prescriptions/
정성 검증 — 정답 라벨 없음, 추출 약품 목록 + 매칭 결과만 기록.
"""
from __future__ import annotations

import asyncio
import base64
import json
import os
from dataclasses import dataclass, field
from pathlib import Path

import asyncpg
import pytest
from langchain_core.messages import HumanMessage
from langchain_core.output_parsers import PydanticOutputParser
from langchain_google_genai import ChatGoogleGenerativeAI

from app.domain.ocr import RawOcrItem, RawOcrItemList
from tests.eval.run_eval_full import _cascade_search

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


@dataclass
class DrugExtractionResult:
    image_name: str
    items: list[RawOcrItem]
    matched: list[dict]
    error: str | None = None
    raw_response: str = field(default="", repr=False)


class RealE2ERunner:
    """실 약봉투 이미지 → Gemini OCR → DB cascade 전체 흐름."""

    def __init__(self, pool: asyncpg.Pool, api_key: str) -> None:
        self._pool = pool
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

    async def run_image(self, image_path: Path) -> DrugExtractionResult:
        image_bytes = image_path.read_bytes()
        mime = _mime_type(image_path)
        encoded = base64.b64encode(image_bytes).decode("ascii")

        message = HumanMessage(
            content=[
                {"type": "text", "text": self._prompt},
                {
                    "type": "image_url",
                    "image_url": f"data:{mime};base64,{encoded}",
                },
            ]
        )

        try:
            result = await self._llm.ainvoke([message])
            raw = getattr(result, "content", str(result))
        except Exception as exc:
            return DrugExtractionResult(
                image_name=image_path.name,
                items=[],
                matched=[],
                error=f"vision_error: {exc.__class__.__name__}: {exc}",
            )

        try:
            parsed = self._parser.parse(raw)
            items = parsed.items
        except Exception as exc:
            return DrugExtractionResult(
                image_name=image_path.name,
                items=[],
                matched=[],
                error=f"parse_error: {exc.__class__.__name__}",
                raw_response=raw[:500],
            )

        matched = []
        for item in items:
            db_name, stage = await _cascade_search(self._pool, item.name_raw)
            matched.append(
                {
                    "name_raw": item.name_raw,
                    "confidence": float(item.confidence),
                    "db_match": db_name,
                    "stage": stage,
                    "dose_amount": float(item.dose_amount) if item.dose_amount else None,
                    "dose_unit": item.dose_unit,
                    "frequency": item.frequency,
                    "duration_days": item.duration_days,
                }
            )

        return DrugExtractionResult(
            image_name=image_path.name,
            items=items,
            matched=matched,
            raw_response=raw[:200],
        )

    async def run_all(self) -> list[DrugExtractionResult]:
        images = sorted(REAL_IMG_DIR.glob("*"))
        images = [p for p in images if p.suffix.lower() in _MIME_MAP]
        results = []
        for img in images:
            r = await self.run_image(img)
            results.append(r)
        return results


@pytest.mark.eval_full
class TestRealE2E:
    async def test_all_8_images_extract(self):
        """8장 실 약봉투 Gemini Vision OCR → DB cascade 추출 검증."""
        if not GEMINI_API_KEY:
            pytest.skip("GEMINI_API_KEY not set")

        pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=4)
        try:
            runner = RealE2ERunner(pool, GEMINI_API_KEY)
            results = await runner.run_all()
        finally:
            await pool.close()

        assert len(results) == 8, f"이미지 8장 기대, 실제: {len(results)}"

        error_count = sum(1 for r in results if r.error)
        print(f"\n[REAL E2E] 8장 처리 완료 — 오류: {error_count}")

        total_drugs = 0
        total_matched = 0
        for r in results:
            if r.error:
                print(f"  ERROR {r.image_name}: {r.error}")
                continue
            matched_cnt = sum(1 for m in r.matched if m["db_match"])
            total_drugs += len(r.matched)
            total_matched += matched_cnt
            print(f"  {r.image_name}: 약품 {len(r.matched)}개 추출, DB 매칭 {matched_cnt}개")
            for m in r.matched:
                status = "✓" if m["db_match"] else "✗"
                print(
                    f"    {status} {m['name_raw']!r}"
                    f" → {m['db_match']!r} (stage={m['stage']})"
                )

        if total_drugs > 0:
            match_rate = total_matched / total_drugs
            print(f"\n  전체 DB 매칭률: {total_matched}/{total_drugs} = {match_rate:.2%}")

        assert error_count <= 2, f"오류 3건 이상: {error_count}/8"

    async def test_report_saved(self):
        """real_e2e 결과를 JSON + MD 보고서로 저장."""
        if not GEMINI_API_KEY:
            pytest.skip("GEMINI_API_KEY not set")

        pool = await asyncpg.create_pool(POSTGRES_DSN, min_size=1, max_size=4)
        try:
            runner = RealE2ERunner(pool, GEMINI_API_KEY)
            results = await runner.run_all()
        finally:
            await pool.close()

        REPORT_DIR.mkdir(parents=True, exist_ok=True)

        report_data = [
            {
                "image": r.image_name,
                "error": r.error,
                "drug_count": len(r.matched),
                "matched_count": sum(1 for m in r.matched if m["db_match"]),
                "drugs": r.matched,
            }
            for r in results
        ]
        out_json = REPORT_DIR / "real_e2e_2026-06-09.json"
        out_json.write_text(json.dumps(report_data, ensure_ascii=False, indent=2))

        assert out_json.exists()
        loaded = json.loads(out_json.read_text())
        assert len(loaded) == 8
