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
FEWSHOT_PROMPT_PATH = Path(__file__).resolve().parent / "prompts" / "system_prompt.txt"

try:
    from google.genai.errors import ClientError as _GenAIClientError
    from google.genai.errors import ServerError as _GenAIServerError

    _RATE_LIMIT_ERRORS = (_GenAIClientError, _GenAIServerError)
except ImportError:
    _RATE_LIMIT_ERRORS = ()


def _mask_key(key: str) -> str:
    if len(key) <= 4:
        return "AIza***"
    return f"AIza***{key[-4:]}"


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
        llm: AsyncChatModel | None = None,
        api_keys: list[str] | None = None,
        model: str = "gemini-2.5-flash",
        prompt: str | None = None,
        timeout_sec: float = VISION_TIMEOUT_SEC,
        fewshot_enabled: bool = False,
        _llms: list[AsyncChatModel] | None = None,
    ):
        if _llms is not None:
            self._llms = _llms
        elif api_keys:
            from langchain_google_genai import ChatGoogleGenerativeAI

            self._llms = [
                ChatGoogleGenerativeAI(model=model, google_api_key=k)
                for k in api_keys
            ]
        elif llm is not None:
            self._llms = [llm]
        else:
            raise ValueError("llm, api_keys, or _llms must be provided")

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
        last_exc: Exception | None = None
        for i, llm in enumerate(self._llms):
            logger.debug("ocr_vision key_in_use=%s key_index=%d", _mask_key(_key_hint(llm)), i)
            try:
                content = await asyncio.wait_for(
                    self._invoke(image_bytes, llm), timeout=self._timeout
                )
                return self._parse(content)
            except asyncio.TimeoutError:
                logger.warning("gemini vision timed out key_index=%d", i)
                raise
            except _RATE_LIMIT_ERRORS as exc:
                last_exc = exc
                if i < len(self._llms) - 1:
                    logger.warning(
                        "key_rotation: key[%d] 429/503 → fallback retry key[%d]", i, i + 1
                    )
                    continue
                logger.critical("ocr_vision all_keys_exhausted after %d keys", len(self._llms))
                raise VisionInvocationError(str(exc)) from exc
            except Exception as exc:
                logger.warning("gemini vision invocation failed: %s", exc.__class__.__name__)
                raise VisionInvocationError(str(exc)) from exc
        raise VisionInvocationError("no llm clients available")

    async def _invoke(self, image_bytes: bytes, llm: AsyncChatModel) -> str:
        mime = _detect_mime_type(image_bytes)
        encoded = base64.b64encode(image_bytes).decode("ascii")
        message = HumanMessage(
            content=[
                {"type": "text", "text": self._prompt},
                {"type": "image_url", "image_url": f"data:{mime};base64,{encoded}"},
            ]
        )
        result = await llm.ainvoke([message])
        return getattr(result, "content", str(result))

    def _parse(self, content: str) -> list[RawOcrItem]:
        try:
            parsed = self._parser.parse(content)
        except Exception as exc:
            logger.warning("ocr response parse failed: %s", exc.__class__.__name__)
            raise OcrParseError(str(exc)) from exc
        return parsed.items


def _key_hint(llm: object) -> str:
    for attr in ("google_api_key", "_google_api_key", "api_key"):
        val = getattr(llm, attr, None)
        if val:
            return str(val)
    return "?"
