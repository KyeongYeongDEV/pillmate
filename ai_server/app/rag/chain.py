from __future__ import annotations

import logging
from pathlib import Path

from app.domain.chat import MFDS_SOURCE, ChatResponse, DrugSource
from app.core.llm import LlmInvoker
from app.rag.retriever import DrugRetriever, RetrievedDrug

logger = logging.getLogger(__name__)

PROMPT_PATH = Path(__file__).parent / "prompts" / "chat_system.txt"
FAITHFULNESS_FLOOR = 0.95
SOURCE_TAG = f"출처: {MFDS_SOURCE}"


class ChatService:
    def __init__(
        self,
        retriever: DrugRetriever,
        llm: LlmInvoker,
        top_k: int = 5,
        prompt_template: str | None = None,
    ):
        self._retriever = retriever
        self._llm = llm
        self._top_k = top_k
        self._prompt_template = prompt_template or _load_prompt_template()

    async def answer(self, question: str) -> ChatResponse:
        documents = await self._retriever.search(question, self._top_k)
        if not documents:
            logger.info("retrieval empty for question_len=%d", len(question))
            return ChatResponse.fallback()

        prompt = self._build_prompt(question, documents)
        raw_answer = (await self._llm.ainvoke(prompt)).strip()

        if not _has_source_tag(raw_answer):
            logger.info("LLM answer missing source tag — fallback")
            return ChatResponse.fallback()

        return ChatResponse(
            answer=raw_answer,
            sources=[_to_source(d) for d in documents],
            faithfulness=FAITHFULNESS_FLOOR,
        )

    def _build_prompt(self, question: str, documents: list[RetrievedDrug]) -> str:
        context = "\n\n".join(doc.to_context_block() for doc in documents)
        return self._prompt_template.replace("{context}", context).replace("{question}", question)


def _has_source_tag(answer: str) -> bool:
    return SOURCE_TAG in answer


def _to_source(drug: RetrievedDrug) -> DrugSource:
    return DrugSource(kd_code=drug.kd_code, name=drug.name, source=MFDS_SOURCE)


def _load_prompt_template() -> str:
    return PROMPT_PATH.read_text(encoding="utf-8")
