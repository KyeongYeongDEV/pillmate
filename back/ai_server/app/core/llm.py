from __future__ import annotations

from typing import Protocol


class LlmInvoker(Protocol):
    async def ainvoke(self, system_prompt: str) -> str:
        ...


class GeminiInvoker(LlmInvoker):
    def __init__(self, api_key: str, model: str):
        from langchain_google_genai import ChatGoogleGenerativeAI

        self._client = ChatGoogleGenerativeAI(
            model=model,
            google_api_key=api_key,
            temperature=0.0,
        )

    async def ainvoke(self, system_prompt: str) -> str:
        result = await self._client.ainvoke(system_prompt)
        return getattr(result, "content", str(result))
