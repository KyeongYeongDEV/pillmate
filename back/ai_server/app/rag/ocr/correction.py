"""OCR 오인식 보정 LLM 어댑터 (Tier 3 fallback)."""
from __future__ import annotations

import asyncio
import json
import logging
import time
from typing import Protocol

from langchain_core.messages import HumanMessage

logger = logging.getLogger(__name__)

# 2026-07-14 오라클 실측 — 20s 상한에서 미해결 4건 전부 timeout 소진(매칭 기여 0)으로
# total 54~68s 의 둘째 병목. flash-lite(thinking OFF) 정상 응답은 수 초 → 8s 로 축소.
CORRECTION_TIMEOUT_SEC = 8.0

try:
    from google.genai.errors import ClientError as _GenAIClientError
    from google.genai.errors import ServerError as _GenAIServerError

    _RATE_LIMIT_ERRORS = (_GenAIClientError, _GenAIServerError)
except ImportError:
    _RATE_LIMIT_ERRORS = ()

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

    def __init__(
        self,
        llm: AsyncChatModel | None = None,
        api_keys: list[str] | None = None,
        model: str = "gemini-2.5-flash-lite",
        _llms: list[AsyncChatModel] | None = None,
        timeout_sec: float = CORRECTION_TIMEOUT_SEC,
    ) -> None:
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
        self._timeout = timeout_sec

    async def correct(self, name_raw: str) -> list[str]:
        prompt = CORRECTION_PROMPT_TEMPLATE.format(name_raw=name_raw)
        for i, llm in enumerate(self._llms):
            t0 = time.monotonic()
            try:
                result = await asyncio.wait_for(
                    llm.ainvoke([HumanMessage(content=prompt)]), timeout=self._timeout
                )
                content = getattr(result, "content", str(result))
                self._log_attempt(t0, "ok", None)
                return self._parse_candidates(content)
            except asyncio.TimeoutError:
                self._log_attempt(t0, "timeout", "TimeoutError")
                logger.warning("ocr correction timed out for '%s'", name_raw)
                return []
            except _RATE_LIMIT_ERRORS as exc:
                self._log_attempt(t0, "api_error", exc.__class__.__name__)
                if i < len(self._llms) - 1:
                    logger.warning(
                        "correction key_rotation: key[%d] 429/503 → fallback retry key[%d]",
                        i, i + 1,
                    )
                    continue
                logger.warning("ocr correction all keys exhausted for '%s'", name_raw)
                return []
            except Exception as exc:
                self._log_attempt(t0, "api_error", exc.__class__.__name__)
                logger.warning("ocr correction failed for '%s'", name_raw)
                return []
        return []

    @staticmethod
    def _log_attempt(t0: float, outcome: str, error_class: str | None) -> None:
        logger.info(
            json.dumps(
                {
                    "event": "correction_attempt",
                    "elapsed_ms": int((time.monotonic() - t0) * 1000),
                    "outcome": outcome,
                    "error_class": error_class,
                },
                ensure_ascii=False,
            )
        )

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
