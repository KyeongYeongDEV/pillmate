FROM python:3.11-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

RUN pip install \
    "fastapi>=0.110" \
    "uvicorn[standard]>=0.30" \
    "pydantic>=2.0" \
    "pydantic-settings>=2.0" \
    "langchain>=0.2" \
    "langchain-google-genai>=2.0" \
    "langchain-postgres>=0.0.12" \
    "asyncpg>=0.29" \
    "httpx>=0.27" \
    "openai>=1.30" \
    "psycopg[binary]>=3.1" \
    "sqlalchemy>=2.0"

COPY app ./app

EXPOSE 8001

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD python -c "import httpx,sys; sys.exit(0 if httpx.get('http://localhost:8001/healthz',timeout=2).status_code==200 else 1)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
