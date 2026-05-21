---
name: fastapi
description: FastAPI AI 서버 코딩 규칙
---

# FastAPI Rules

## 환경

- Python 3.11+
- FastAPI 0.110+
- Pydantic v2
- uv 또는 poetry로 의존성 관리

## 패키지 구조

```
ai_server/
├── app/
│   ├── api/              # 라우터
│   ├── core/             # 설정, LLM 클라이언트, 캐시
│   ├── rag/              # RAG 컴포넌트
│   ├── domain/           # 순수 도메인 (의존성 없음)
│   └── main.py
├── tests/                # pytest
└── pyproject.toml
```

## 라우터

- 라우터는 얇게 (HTTP ↔ Pydantic ↔ Service)
- 비즈니스 로직 금지
- 응답 모델 명시 (`response_model=ResponseSchema`)

```python
@router.post("/ocr/prescription", response_model=OcrResponse)
async def ocr_prescription(
    request: OcrRequest,
    service: OcrService = Depends(get_ocr_service),
) -> OcrResponse:
    return await service.process(request)
```

## Pydantic

- v2 사용 (`model_config = ConfigDict(...)`)
- 모든 외부 입력/출력은 Pydantic
- LLM 응답 파싱은 `PydanticOutputParser` (LangChain)

## 비동기

- 외부 호출은 모두 `async` (Gemini, S3, Redis, DB)
- DB는 `asyncpg` 또는 `SQLAlchemy 2.0 async`
- 동기 작업이 필요하면 `run_in_executor`

## LLM 호출

- 직접 호출 금지, 항상 LangChain 추상화 통해
- 캐시 우선 (`@cached` 또는 Redis check)
- 토큰 한도 초과 시 청크 분할 재시도

## 에러 처리

```python
@app.exception_handler(LLMTimeoutError)
async def llm_timeout_handler(request, exc):
    return JSONResponse(status_code=504, content={"error": "AI 응답 지연"})
```

- 도메인 예외 분리 (`LLMTimeoutError`, `RagRetrievalError`)
- 모든 예외는 Sentry로 전송
- 환자 정보 포함 로그 금지

## 테스트

- pytest + httpx AsyncClient
- LLM/Gemini는 Mock (실제 호출 금지, CI 비용)
- 통합 테스트는 별도 마커 (`@pytest.mark.integration`)

## 금지

- 동기 외부 호출
- 환경변수 직접 `os.environ` (pydantic-settings 사용)
- 환자 정보 로그
- LLM 응답을 그대로 반환 (검증 레이어 필수)
