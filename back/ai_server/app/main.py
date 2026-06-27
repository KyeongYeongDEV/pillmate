from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from app.api import analyze as analyze_api
from app.api import chat as chat_api
from app.api import ocr as ocr_api
from app.core.config import get_settings
from app.core.sentry import init_sentry
from app.core.db import build_pool
from app.core.llm import GeminiInvoker
from app.rag.chain import ChatService
from app.rag.ocr.drug_search import (
    AsyncpgIlikeSearch,
    AsyncpgIngredientSearch,
    PgVectorDrugSearch,
)
from app.rag.health_report.service import HealthReportService
from app.rag.prescription_recommendation.service import PrescriptionRecommendationService
from app.rag.ocr.image_fetcher import HttpxImageFetcher
from app.rag.ocr.matcher import DrugMatcher
from app.rag.ocr.service import OcrPrescriptionService
from app.rag.pgvector_retriever import OpenAIEmbeddingAdapter, PgVectorRetriever

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s :: %(message)s")
logger = logging.getLogger("ai_server")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    init_sentry(dsn=settings.sentry_dsn, environment=settings.environment)
    dsn = (
        f"postgresql://{settings.postgres_user}:{settings.postgres_password}"
        f"@{settings.postgres_host}:{settings.postgres_port}/{settings.postgres_db}"
    )
    pool = await build_pool(dsn)
    embedder = OpenAIEmbeddingAdapter(
        api_key=settings.effective_openai_key,
        model=settings.embedding_model,
        dimensions=settings.embedding_dim,
    )
    retriever = PgVectorRetriever(pool=pool, embedder=embedder)
    llm = GeminiInvoker(api_keys=settings.gemini_keys, model=settings.gemini_model)
    service = ChatService(retriever=retriever, llm=llm, top_k=settings.retrieval_top_k)

    ocr_service = _build_ocr_service(pool=pool, retriever=retriever, settings=settings)
    health_report_service = HealthReportService(llm=_GeminiLlmRunner(llm=llm))
    recommendation_service = PrescriptionRecommendationService(llm=_GeminiLlmRunner(llm=llm))

    app.dependency_overrides[chat_api.get_chat_service] = lambda: service
    app.dependency_overrides[ocr_api.get_ocr_service] = lambda: ocr_service
    app.dependency_overrides[analyze_api.get_health_report_service] = lambda: health_report_service
    app.dependency_overrides[analyze_api.get_prescription_recommendation_service] = (
        lambda: recommendation_service
    )
    try:
        yield
    finally:
        await pool.close()


def _build_ocr_service(pool, retriever, settings) -> OcrPrescriptionService:
    from app.rag.ocr.correction import OcrCorrectionAdapter
    from app.rag.ocr.pill_identify import PillIdentifyAdapter
    from app.rag.ocr.preprocess import ImagePreprocessor
    from app.rag.ocr.vision import GeminiVisionAdapter

    if settings.drug_matcher_impl == "rrf":
        from app.rag.ocr.rrf_factory import build_rrf_matcher
        matcher = build_rrf_matcher(pool=pool)
    else:
        matcher = DrugMatcher(
            ilike=AsyncpgIlikeSearch(pool=pool),
            vector=PgVectorDrugSearch(retriever=retriever),
            ingredient=AsyncpgIngredientSearch(pool=pool),
        )
    vision = GeminiVisionAdapter(
        api_keys=settings.gemini_keys,
        model=settings.gemini_model,
        fewshot_enabled=settings.ocr_fewshot_enabled,
    )
    correction = OcrCorrectionAdapter(api_keys=settings.gemini_keys, model=settings.gemini_model)
    preprocessor = ImagePreprocessor() if settings.ocr_preprocess_enabled else None
    pill_identifier = PillIdentifyAdapter(pool=pool) if settings.pill_identify_enabled else None
    return OcrPrescriptionService(
        fetcher=HttpxImageFetcher(),
        vision=vision,
        matcher=matcher,
        correction=correction,
        preprocessor=preprocessor,
        pill_identifier=pill_identifier,
    )


class _GeminiLlmRunner:
    def __init__(self, llm: GeminiInvoker):
        self._llm = llm

    async def invoke(self, system: str, user: str) -> str:
        combined = f"{system}\n\n---\n{user}"
        return await self._llm.ainvoke(combined)


def create_app() -> FastAPI:
    app = FastAPI(title="PillMate AI", version="0.1.0", lifespan=lifespan)
    app.include_router(chat_api.router)
    app.include_router(ocr_api.router)
    app.include_router(analyze_api.router)
    Instrumentator(
        should_group_status_codes=True,
        excluded_handlers=["/healthz", "/api/v1/health"],
    ).instrument(app).expose(app, endpoint="/metrics")
    return app


app = create_app()


@app.get("/api/v1/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/healthz")
async def healthz() -> dict[str, str]:
    return {"status": "ok"}
