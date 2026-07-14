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
from app.rag.ocr.cache import NullOcrResultCache, RedisOcrResultCache
from app.rag.ocr.image_fetcher import HttpxImageFetcher
from app.rag.ocr.matcher import DrugMatcher
from app.rag.ocr.reranker import BgeRerankerAdapter
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
    llm = GeminiInvoker(api_keys=settings.gemini_key_list, model=settings.gemini_model)
    service = ChatService(retriever=retriever, llm=llm, top_k=settings.retrieval_top_k)

    bge_reranker = BgeRerankerAdapter()
    _warmup_bge(bge_reranker)
    ocr_cache = await _build_ocr_cache(settings)
    ocr_service = _build_ocr_service(
        pool=pool, retriever=retriever, settings=settings,
        bge_reranker=bge_reranker, cache=ocr_cache,
    )
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


def _warmup_bge(bge_reranker: BgeRerankerAdapter) -> None:
    # 부팅 시 dummy encode 로 BGE 모델 사전 로드 → 첫 요청 -60초. 실패해도 정상 부팅 유지.
    import time
    started = time.monotonic()
    try:
        bge_reranker.warmup()
        elapsed = time.monotonic() - started
        logger.info("bge_warmup completed elapsed=%.1fs", elapsed)
    except Exception as exc:
        logger.warning("bge_warmup failed — first request will pay cold start: %s", exc.__class__.__name__)


async def _build_ocr_cache(settings):
    # cost-aware: 동일 이미지 OCR 재요청 sub-second 반환. 실패 시 NullCache 로 fallback (부팅 유지).
    try:
        from redis import asyncio as redis_asyncio
        client = redis_asyncio.Redis(
            host=settings.redis_host, port=settings.redis_port,
            decode_responses=False,
        )
        await client.ping()
        logger.info("ocr_cache redis connected host=%s port=%d", settings.redis_host, settings.redis_port)
        return RedisOcrResultCache(redis_client=client)
    except Exception as exc:
        logger.warning("ocr_cache redis unavailable — using NullCache: %s", exc.__class__.__name__)
        return NullOcrResultCache()


def _resolve_vision_variant(settings) -> str:
    variant = settings.ocr_vision_variant
    if variant == "auto":
        return "lite" if "lite" in settings.gemini_model else "flash"
    return variant


def _build_vision(settings):
    variant = _resolve_vision_variant(settings)
    if variant == "cascade":
        from app.rag.ocr.cascade_vision import CASCADE_PRIMARY_TIMEOUT_SEC, CascadeVisionAdapter
        from app.rag.ocr.vision import GeminiVisionAdapter
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter
        primary = GeminiVisionLiteAdapter(
            api_keys=settings.gemini_key_list, model="gemini-2.5-flash-lite",
            fewshot_enabled=settings.ocr_fewshot_enabled,
            timeout_sec=CASCADE_PRIMARY_TIMEOUT_SEC,
        )
        fallback = GeminiVisionAdapter(
            api_keys=settings.gemini_key_list, model="gemini-2.5-flash",
            fewshot_enabled=settings.ocr_fewshot_enabled,
        )
        logger.info("OCR vision adapter=cascade primary=flash-lite fallback=flash")
        return CascadeVisionAdapter(primary, fallback)
    logger.info("OCR vision adapter=%s model=%s", variant, settings.gemini_model)
    if variant == "lite":
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter
        return GeminiVisionLiteAdapter(
            api_keys=settings.gemini_key_list,
            model=settings.gemini_model,
            fewshot_enabled=settings.ocr_fewshot_enabled,
        )
    from app.rag.ocr.vision import GeminiVisionAdapter
    return GeminiVisionAdapter(
        api_keys=settings.gemini_key_list,
        model=settings.gemini_model,
        fewshot_enabled=settings.ocr_fewshot_enabled,
    )


def _build_ocr_service(
    pool, retriever, settings,
    bge_reranker: BgeRerankerAdapter | None = None,
    cache=None,
) -> OcrPrescriptionService:
    from app.rag.ocr.correction import OcrCorrectionAdapter
    from app.rag.ocr.pill_identify import PillIdentifyAdapter
    from app.rag.ocr.preprocess import ImagePreprocessor

    if settings.drug_matcher_impl == "rrf":
        from app.rag.ocr.rrf_factory import build_rrf_matcher
        matcher = build_rrf_matcher(pool=pool, bge_reranker=bge_reranker)
    else:
        matcher = DrugMatcher(
            ilike=AsyncpgIlikeSearch(pool=pool),
            vector=PgVectorDrugSearch(retriever=retriever),
            ingredient=AsyncpgIngredientSearch(pool=pool),
        )
    vision = _build_vision(settings)
    # correction 은 어댑터 기본 flash-lite 사용 — vision 모델(flash) override 가 20s timeout 소진 병목이었음 (2026-07-14 실측)
    correction = OcrCorrectionAdapter(api_keys=settings.gemini_key_list)
    preprocessor = ImagePreprocessor() if settings.ocr_preprocess_enabled else None
    pill_identifier = PillIdentifyAdapter(pool=pool) if settings.pill_identify_enabled else None
    return OcrPrescriptionService(
        fetcher=HttpxImageFetcher(),
        vision=vision,
        matcher=matcher,
        cache=cache,
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
