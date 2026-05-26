from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


MFDS_SOURCE = "식품의약품안전처"
FALLBACK_ANSWER = "정확한 정보를 확인할 수 없습니다. 약사 또는 의사와 상담해 주세요."


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    question: str = Field(min_length=1, max_length=500, description="사용자 자연어 질문")


class DrugSource(BaseModel):
    model_config = ConfigDict(extra="forbid")

    kd_code: str = Field(alias="kdCode")
    name: str
    source: str = MFDS_SOURCE

    model_config = ConfigDict(populate_by_name=True, extra="forbid")


class ChatResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    answer: str
    sources: list[DrugSource] = Field(default_factory=list)
    faithfulness: float | None = None

    @classmethod
    def fallback(cls) -> "ChatResponse":
        return cls(answer=FALLBACK_ANSWER, sources=[], faithfulness=None)
