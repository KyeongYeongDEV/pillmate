"""OCR 오인식 보정 LLM 어댑터 (Tier 3 fallback)."""
from __future__ import annotations

import json
import logging
from typing import Protocol

from langchain_core.messages import HumanMessage

logger = logging.getLogger(__name__)

CORRECTION_PROMPT_TEMPLATE = (
    "당신은 한국 식약처(식품의약품안전처) 등록 약품명 전문가입니다.\n"
    "OCR이 잘못 인식한 약품명 '{name_raw}' 의 올바른 식약처 표준 약품명 후보를\n"
    "한국어로 최대 3개 추정하세요. 반드시 실제 존재할 가능성이 높은 이름만 나열하세요.\n\n"
    "응답은 반드시 아래 JSON 형식으로만 답하세요 (다른 텍스트 금지):\n"
    '{{"candidates": ["후보1", "후보2", "후보3"]}}\n\n'
    '후보가 없으면: {{"candidates": []}}'
)


class AsyncChatModel(Protocol):
    async def ainvoke(self, messages: list) -> object: ...


class OcrCorrectionAdapter:
    """cascade 전체 실패 시 Gemini Flash 로 OCR 오인식 보정 top-3 추정."""

    def __init__(self, llm: AsyncChatModel) -> None:
        self._llm = llm

    async def correct(self, name_raw: str) -> list[str]:
        prompt = CORRECTION_PROMPT_TEMPLATE.format(name_raw=name_raw)
        try:
            result = await self._llm.ainvoke([HumanMessage(content=prompt)])
            content = getattr(result, "content", str(result))
            return self._parse_candidates(content)
        except Exception:
            logger.warning("ocr correction failed for '%s'", name_raw)
            return []

    def _parse_candidates(self, content: str) -> list[str]:
        try:
            start = content.find("{")
            end = content.rfind("}") + 1
            if start == -1 or end == 0:
                return []
            data = json.loads(content[start:end])
            candidates = data.get("candidates", [])
            return [c for c in candidates if isinstance(c, str) and c.strip()]
        except (json.JSONDecodeError, AttributeError):
            return []
