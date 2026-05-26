from __future__ import annotations

from typing import Iterable

import pytest
from fastapi.testclient import TestClient

from app.api import chat as chat_api
from app.domain.chat import FALLBACK_ANSWER, MFDS_SOURCE
from app.main import create_app
from app.rag.chain import ChatService
from app.rag.retriever import DrugRetriever, RetrievedDrug


SAMPLE_DRUG = RetrievedDrug(
    kd_code="200006427",
    name="타이레놀정500밀리그람",
    efficacy="해열, 진통",
    dosage="1회 1-2정, 1일 3-4회",
    main_ingr="Acetaminophen 500mg",
)


class StubRetriever(DrugRetriever):
    def __init__(self, results: Iterable[RetrievedDrug]):
        self._results = list(results)

    async def search(self, query: str, top_k: int) -> list[RetrievedDrug]:
        return list(self._results[:top_k])


class StubLlm:
    def __init__(self, response: str):
        self._response = response

    async def ainvoke(self, system_prompt: str) -> str:
        return self._response


@pytest.fixture
def app_with_overrides():
    def _make(service: ChatService):
        app = create_app()
        # lifespan 은 TestClient 가 호출하지만 dependency_overrides 가 우선한다.
        app.dependency_overrides[chat_api.get_chat_service] = lambda: service
        return app

    return _make


def _build_service(retriever: DrugRetriever, llm_response: str) -> ChatService:
    return ChatService(retriever=retriever, llm=StubLlm(llm_response), top_k=5)


def test_chat_returns_answer_with_source_when_retrieval_succeeds(app_with_overrides):
    service = _build_service(
        StubRetriever([SAMPLE_DRUG]),
        f"타이레놀은 해열·진통에 사용합니다. 출처: {MFDS_SOURCE}",
    )
    client = TestClient(app_with_overrides(service))

    response = client.post("/api/v1/chat", json={"question": "타이레놀은 어떤 약인가요?"})

    assert response.status_code == 200
    body = response.json()
    assert MFDS_SOURCE in body["answer"]
    assert len(body["sources"]) == 1
    assert body["sources"][0]["kdCode"] == "200006427"
    assert body["sources"][0]["source"] == MFDS_SOURCE
    assert body["faithfulness"] is not None


def test_chat_returns_fallback_when_retrieval_empty(app_with_overrides):
    service = _build_service(StubRetriever([]), "irrelevant answer without source")
    client = TestClient(app_with_overrides(service))

    response = client.post("/api/v1/chat", json={"question": "알 수 없는 약입니다"})

    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == FALLBACK_ANSWER
    assert body["sources"] == []
    assert body["faithfulness"] is None


def test_chat_returns_fallback_when_llm_omits_source(app_with_overrides):
    service = _build_service(StubRetriever([SAMPLE_DRUG]), "근거 없는 응답")
    client = TestClient(app_with_overrides(service))

    response = client.post("/api/v1/chat", json={"question": "타이레놀은 어떤 약인가요?"})

    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == FALLBACK_ANSWER
    assert body["sources"] == []


def test_chat_rejects_empty_question(app_with_overrides):
    service = _build_service(StubRetriever([SAMPLE_DRUG]), f"... 출처: {MFDS_SOURCE}")
    client = TestClient(app_with_overrides(service))

    response = client.post("/api/v1/chat", json={"question": ""})

    assert response.status_code == 422
