"""Smoke test: /metrics 엔드포인트가 200 + prometheus 형식 텍스트를 반환하는지 검증.
전체 lifespan(DB 연결 등) 없이 독립 미니 앱으로 검증.
"""
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from prometheus_fastapi_instrumentator import Instrumentator


@pytest.fixture
def instrumented_app():
    app = FastAPI()
    Instrumentator().instrument(app).expose(app, endpoint="/metrics")

    @app.get("/ping")
    def ping():
        return {"status": "ok"}

    return app


def test_metrics_endpoint_returns_200(instrumented_app):
    client = TestClient(instrumented_app)
    client.get("/ping")  # generate at least one request metric

    response = client.get("/metrics")

    assert response.status_code == 200


def test_metrics_endpoint_contains_http_request_metric(instrumented_app):
    client = TestClient(instrumented_app)
    client.get("/ping")

    response = client.get("/metrics")

    assert "http_request" in response.text


def test_metrics_endpoint_content_type_is_prometheus(instrumented_app):
    client = TestClient(instrumented_app)

    response = client.get("/metrics")

    assert "text/plain" in response.headers.get("content-type", "")
