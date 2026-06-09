from __future__ import annotations

import asyncio
import base64
import logging
from pathlib import Path
from typing import Protocol

from langchain_core.messages import HumanMessage
from langchain_core.output_parsers import PydanticOutputParser

from app.domain.ocr import RawOcrItem, RawOcrItemList
from app.exceptions import OcrParseError, VisionInvocationError

logger = logging.getLogger(__name__)

VISION_TIMEOUT_SEC = 30.0

# 기본 프롬프트 (Phase B-4 이전)
PROMPT_PATH = Path(__file__).resolve().parent.parent / "prompts" / "ocr_system.txt"

# Few-shot 강화 프롬프트 (Phase B-6, FEWSHOT_ENABLED=true 시 사용)
FEWSHOT_PROMPT_PATH = Path(__file__).resolve().parent / "prompts" / "system_prompt.txt"


def _detect_mime_type(image_bytes: bytes) -> str:
    if image_bytes[:4] == b"\x89PNG":
        return "image/png"
    if image_bytes[:2] == b"\xff\xd8":
        return "image/jpeg"
    return "image/jpeg"


class AsyncChatModel(Protocol):
    async def ainvoke(self, messages: list[HumanMessage]) -> object: ...


class GeminiVisionAdapter:
    def __init__(
        self,
        llm: AsyncChatModel,
        prompt: str | None = None,
        timeout_sec: float = VISION_TIMEOUT_SEC,
        fewshot_enabled: bool = False,
    ):
        self._llm = llm
        self._parser = PydanticOutputParser(pydantic_object=RawOcrItemList)
        raw_prompt = prompt or self._resolve_prompt(fewshot_enabled)
        self._prompt = raw_prompt.replace(
            "{format_instructions}", self._parser.get_format_instructions()
        )
        self._timeout = timeout_sec

    @staticmethod
    def _resolve_prompt(fewshot_enabled: bool) -> str:
        if fewshot_enabled and FEWSHOT_PROMPT_PATH.exists():
            return FEWSHOT_PROMPT_PATH.read_text(encoding="utf-8")
        return PROMPT_PATH.read_text(encoding="utf-8")

    async def extract(self, image_bytes: bytes) -> list[RawOcrItem]:
        try:
            content = await asyncio.wait_for(self._invoke(image_bytes), timeout=self._timeout)
        except asyncio.TimeoutError:
            logger.warning("gemini vision timed out")
            raise
        except Exception as exc:
            logger.warning("gemini vision invocation failed: %s", exc.__class__.__name__)
            raise VisionInvocationError(str(exc)) from exc
        return self._parse(content)

    async def _invoke(self, image_bytes: bytes) -> str:
        mime = _detect_mime_type(image_bytes)
        encoded = base64.b64encode(image_bytes).decode("ascii")
        message = HumanMessage(
            content=[
                {"type": "text", "text": self._prompt},
                {"type": "image_url", "image_url": f"data:{mime};base64,{encoded}"},
            ]
        )
        result = await self._llm.ainvoke([message])
        return getattr(result, "content", str(result))

    def _parse(self, content: str) -> list[RawOcrItem]:
        try:
            parsed = self._parser.parse(content)
        except Exception as exc:
            logger.warning("ocr response parse failed: %s", exc.__class__.__name__)
            raise OcrParseError(str(exc)) from exc
        return parsed.items
