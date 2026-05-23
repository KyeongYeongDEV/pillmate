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
PROMPT_PATH = Path(__file__).resolve().parent.parent / "prompts" / "ocr_system.txt"


class AsyncChatModel(Protocol):
    async def ainvoke(self, messages: list[HumanMessage]) -> object: ...


class GeminiVisionAdapter:
    def __init__(
        self,
        llm: AsyncChatModel,
        prompt: str | None = None,
        timeout_sec: float = VISION_TIMEOUT_SEC,
    ):
        self._llm = llm
        self._parser = PydanticOutputParser(pydantic_object=RawOcrItemList)
        self._prompt = (prompt or PROMPT_PATH.read_text(encoding="utf-8")).replace(
            "{format_instructions}", self._parser.get_format_instructions()
        )
        self._timeout = timeout_sec

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
        encoded = base64.b64encode(image_bytes).decode("ascii")
        message = HumanMessage(
            content=[
                {"type": "text", "text": self._prompt},
                {"type": "image_url", "image_url": f"data:image/jpeg;base64,{encoded}"},
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
