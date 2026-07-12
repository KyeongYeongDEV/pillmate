"""
RrfMatcher 팩토리 — main.py 와 eval 스크립트가 동일 인스턴스 구성을 공유.

이 파일이 단일 진실 공급원(single source of truth).
main.py 와 run_eval_full.py 모두 여기서 build_rrf_matcher() 를 import.
둘이 다른 코드를 쓰면 평가≠운영 괴리가 생긴다 — 그걸 이 파일이 원천 차단한다.
"""
from __future__ import annotations

from typing import Any

from app.rag.ocr.decider import MatchDecider
from app.rag.ocr.fuzzy_search import JamoFuzzyRanker, TrigramFuzzySearch
from app.rag.ocr.reranker import BgeRerankerAdapter, DomainReranker
from app.rag.ocr.rrf_adapters import (
    IlikeMultiAdapter,
    IngredientMultiAdapter,
    PrefixRelaxMultiAdapter,
    StrongExactAdapter,
    TokenIlikeMultiAdapter,
    TrigramMultiAdapter,
)
from app.rag.ocr.rrf_matcher import RrfMatcher
from app.rag.ocr.rrf_wire import RrfMatcherAdapter


def build_rrf_matcher_inner(pool: Any, bge_reranker: BgeRerankerAdapter | None = None) -> RrfMatcher:
    """Gate A++ RrfMatcher — eval 스크립트가 match(parsed) 직접 호출 시 사용.

    main.py 와 run_eval_full.py 가 동일 구성을 공유하도록 이 함수가 단일 진실 공급원.
    lifespan 에서 warmup 된 BgeRerankerAdapter 를 주입 가능 — 미주입 시 새 인스턴스 (cold).
    """
    return RrfMatcher(
        exact_single=StrongExactAdapter(pool=pool),
        retrievers={
            "ilike": IlikeMultiAdapter(pool=pool),
            "trigram": TrigramMultiAdapter(
                trgm_search=TrigramFuzzySearch(pool=pool),
                ranker=JamoFuzzyRanker(),
            ),
            "token_ilike": TokenIlikeMultiAdapter(pool=pool),
            "prefix_relax": PrefixRelaxMultiAdapter(pool=pool),
            "ingredient": IngredientMultiAdapter(pool=pool),
        },
        reranker=DomainReranker(),
        bge_reranker=bge_reranker or BgeRerankerAdapter(),
        decider=MatchDecider(),
    )


def build_rrf_matcher(pool: Any, bge_reranker: BgeRerankerAdapter | None = None) -> RrfMatcherAdapter:
    """DrugMatcherPort 호환 어댑터 — OcrPrescriptionService 주입용."""
    return RrfMatcherAdapter(rrf_matcher=build_rrf_matcher_inner(pool, bge_reranker=bge_reranker))
