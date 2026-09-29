# syntax=docker/dockerfile:1.6
FROM python:3.11-slim AS builder

WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# torch 는 CPU 전용 인덱스로 먼저 명시 설치 — CPU-only ARM64 서버인데 기본 PyPI 인덱스로
# FlagEmbedding→sentence_transformers 가 torch 를 끌어오면 CUDA 빌드(nvidia-cublas/cudnn/triton
# 등 수백MB, cuda-toolkit 포함)로 해석돼 빌드가 25분 CI 타임아웃을 넘긴다(2026-09-29 실사고).
# 먼저 CPU 휠을 깔아두면 이후 설치에서 이미 만족된 의존성으로 취급돼 CUDA 변형을 안 받는다.
RUN --mount=type=cache,target=/root/.cache/pip pip install --user \
    --index-url https://download.pytorch.org/whl/cpu \
    "torch>=2.0,<3.0"

# 의존성 먼저 — 소스 변경과 무관한 레이어. cache mount 로 wheel 재사용
RUN --mount=type=cache,target=/root/.cache/pip pip install --user \
    "fastapi>=0.110" \
    "uvicorn[standard]>=0.30" \
    "pydantic>=2.0" \
    "pydantic-settings>=2.0" \
    "langchain>=0.2" \
    "langchain-google-genai>=4.0" \
    "langchain-postgres>=0.0.12" \
    "google-genai>=1.0" \
    "asyncpg>=0.29" \
    "httpx>=0.27" \
    "openai>=1.30" \
    "psycopg[binary]>=3.1" \
    "sqlalchemy>=2.0" \
    "jamotools>=0.1.10" \
    "python-Levenshtein>=0.25.1" \
    "opencv-python-headless>=4.9" \
    "numpy>=1.26" \
    "Pillow>=10.0" \
    "redis>=5.0" \
    "transformers>=4.44.2,<5.0" \
    "FlagEmbedding>=1.2" \
    "prometheus-fastapi-instrumentator>=0.11" \
    "sentry-sdk>=2.0"

FROM python:3.11-slim AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && rm -rf /var/lib/apt/lists/*

ENV PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PATH=/root/.local/bin:$PATH \
    TZ=Asia/Seoul

# builder 에서 설치한 site-packages 만 복사 — 빌드 툴/캐시 제외로 이미지 축소
COPY --from=builder /root/.local /root/.local

# 소스는 마지막 — 코드 변경 시 의존성 레이어 재사용
COPY app ./app

EXPOSE 8001

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD python -c "import httpx,sys; sys.exit(0 if httpx.get('http://localhost:8001/healthz',timeout=2).status_code==200 else 1)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
