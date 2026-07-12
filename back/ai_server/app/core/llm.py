from __future__ import annotations

import logging
from typing import Protocol

logger = logging.getLogger(__name__)

try:
    from google.genai.errors import ClientError as _GenAIClientError
    from google.genai.errors import ServerError as _GenAIServerError

    _RATE_LIMIT_ERRORS = (_GenAIClientError, _GenAIServerError)
except ImportError:
    _RATE_LIMIT_ERRORS = ()


class LlmInvoker(Protocol):
    async def ainvoke(self, system_prompt: str) -> str:
        ...


class GeminiInvoker(LlmInvoker):
    def __init__(self, api_keys: list[str], model: str):
        from langchain_google_genai import ChatGoogleGenerativeAI

        if not api_keys:
            raise RuntimeError("GEMINI_API_KEY 환경변수 필요")
        self._clients = [
            ChatGoogleGenerativeAI(model=model, google_api_key=k, temperature=0.0)
            for k in api_keys
        ]

    async def ainvoke(self, system_prompt: str) -> str:
        for i, client in enumerate(self._clients):
            try:
                result = await client.ainvoke(system_prompt)
                return getattr(result, "content", str(result))
            except _RATE_LIMIT_ERRORS:
                if i < len(self._clients) - 1:
                    logger.warning("gemini_invoker key_rotation: key[%d] 429/503 → fallback", i)
                    continue
                raise
