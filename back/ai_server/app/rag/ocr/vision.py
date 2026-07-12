from __future__ import annotations

import asyncio
import base64
import json
import logging
import time
from pathlib import Path
from typing import Protocol

from langchain_core.messages import HumanMessage
from langchain_core.output_parsers import PydanticOutputParser

from app.domain.ocr import RawOcrItem, RawOcrItemList, RawOcrItems
from app.exceptions import OcrParseError, VisionBusyError, VisionInvocationError
from app.rag.ocr.image_preprocess import resize_max_edge
from app.rag.ocr.lenient_parse import parse_items_leniently
from app.rag.ocr.vision_metrics import record_attempt

logger = logging.getLogger(__name__)

# 2026-07-11 사용자 최종 결정 — 시간 기반 예산제 폐기. 정상 소요(17~18s)와 실제 congestion 시
# 필요 시간의 편차가 커서 어떤 타임아웃/예산 값을 골라도 정상 요청을 계속 오탐 실패시켰다(23:55 실패 사례).
# 남기는 안전망은 단 하나: per-call 150s(BE read timeout 170s 안쪽, 순수 행(hang) 방지용).
# 재시도는 에러(파싱실패/API에러/타임아웃) 시 정확히 1회만 — 타이머·백오프 없이 즉시.
VISION_TIMEOUT_SEC = 150.0
VISION_MAX_ERROR_RETRIES = 1

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
        model: str = "gemini-2.5-flash-lite",
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
                # response_mime_type=JSON 모드 — 파싱실패율을 낮추는 안전한 structured output 적용
                # (전체 response_schema 바인딩은 few-shot/appearance 중첩 스키마와 충돌 위험 있어 보류)
                ChatGoogleGenerativeAI(
                    model=model, google_api_key=k, response_mime_type="application/json"
                )
                for k in api_keys
            ]
        elif llm is not None:
            self._llms = [llm]
        else:
            raise ValueError("llm, api_keys, or _llms must be provided")

        self._model_name = model
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
        key_index = 0
        error_retry_count = 0
        attempt = 0
        while True:
            llm = self._llms[key_index]
            attempt += 1
            record_attempt()
            t0 = time.monotonic()
            try:
                content = await asyncio.wait_for(
                    self._invoke(image_bytes, llm), timeout=self._timeout
                )
                items = self._parse(content)
                logger.info(self._attempt_log_json(attempt, t0, "ok", None))
                return items
            except asyncio.TimeoutError as exc:
                logger.warning(self._attempt_log_json(attempt, t0, "timeout", "TimeoutError"))
                last_exc = exc
            except _RATE_LIMIT_ERRORS as exc:
                logger.warning(self._attempt_log_json(attempt, t0, "api_error", exc.__class__.__name__))
                last_exc = exc
                if key_index < len(self._llms) - 1:
                    logger.warning(
                        "key_rotation: key[%d] 429/503 → fallback retry key[%d]", key_index, key_index + 1
                    )
                    key_index += 1
                    continue
                logger.critical("ocr_vision all_keys_exhausted after %d keys", len(self._llms))
                raise VisionInvocationError(str(exc)) from exc
            except OcrParseError as exc:
                logger.warning(self._attempt_log_json(attempt, t0, "parse_fail", exc.__class__.__name__))
                last_exc = exc
            except Exception as exc:
                logger.warning(self._attempt_log_json(attempt, t0, "api_error", exc.__class__.__name__))
                last_exc = exc

            error_retry_count += 1
            if error_retry_count > VISION_MAX_ERROR_RETRIES:
                logger.critical("ocr_vision error_retry_exhausted attempts=%d", attempt)
                raise VisionBusyError(str(last_exc)) from last_exc
            # 타이머/백오프 없이 즉시 1회 재시도 (사용자 결정 — 예산제 폐기)

    def _attempt_log_json(
        self, attempt: int, t0: float, outcome: str, error_class: str | None
    ) -> str:
        elapsed_ms = int((time.monotonic() - t0) * 1000)
        return json.dumps(
            {
                "event": "vision_attempt",
                "attempt": attempt,
                "elapsed_ms": elapsed_ms,
                "outcome": outcome,
                "error_class": error_class,
                "model": self._model_name,
            },
            ensure_ascii=False,
        )

    async def _invoke(self, image_bytes: bytes, llm: AsyncChatModel) -> str:
        image_bytes = resize_max_edge(image_bytes)
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
            return RawOcrItems(parsed.items, parsed.has_resident_number)
        except Exception as exc:
            logger.warning(
                "ocr response parse failed: %s — attempting partial recovery", exc.__class__.__name__
            )
            return self._parse_partial(content, exc)

    def _parse_partial(self, content: str, original_exc: Exception) -> list[RawOcrItem]:
        try:
            result = parse_items_leniently(content, RawOcrItem)
        except Exception:
            raise OcrParseError(str(original_exc)) from original_exc
        if not result.items:
            raise OcrParseError(str(original_exc)) from original_exc
        logger.warning(
            "ocr partial_recovery kept=%d dropped=%d", len(result.items), result.dropped_count
        )
        return RawOcrItems(result.items, result.has_resident_number)


def _key_hint(llm: object) -> str:
    for attr in ("google_api_key", "_google_api_key", "api_key"):
        val = getattr(llm, attr, None)
        if val:
            return str(val)
    return "?"
