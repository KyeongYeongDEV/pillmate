from __future__ import annotations

import asyncio
import math
from typing import Protocol

from app.rag.ocr.decider import MatchDecider
from app.rag.ocr.matcher import MatchResult
from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.reranker import BgeRerankerAdapter, DomainReranker
from app.rag.ocr.rrf import (
    RRF_K,
    Candidate,
    MatchDecision,
    MatchDecisionType,
    fuse_rrf,
)

_RERANK_TOP_N = 30
_SIGMOID_GAIN = 2.0


def _sigmoid(x: float, gain: float = _SIGMOID_GAIN) -> float:
    """DomainReranker-only 경로에서 final_score 를 (0,1) 로 정규화."""
    return 1.0 / (1.0 + math.exp(-gain * x))


def _apply_sigmoid(candidates: list[Candidate]) -> None:
    """BGE 미적용 시 DomainReranker 점수를 sigmoid(score * gain) 으로 변환."""
    for c in candidates:
        c.final_score = _sigmoid(c.final_score)


class ExactSinglePort(Protocol):
    async def search_single(self, parsed: ParsedItem) -> Candidate | None: ...


class MultiRetrieverPort(Protocol):
    async def search(self, parsed: ParsedItem) -> list[Candidate]: ...


class RrfMatcher:
    def __init__(
        self,
        exact_single: ExactSinglePort,
        retrievers: dict[str, MultiRetrieverPort],
        reranker: DomainReranker | None = None,
        bge_reranker: BgeRerankerAdapter | None = None,
        decider: MatchDecider | None = None,
    ) -> None:
        self._exact_single = exact_single
        self._retrievers = retrievers
        self._reranker = reranker or DomainReranker()
        self._bge_reranker = bge_reranker
        self._decider = decider or MatchDecider()

    async def match(self, parsed: ParsedItem) -> MatchResult:
        if not parsed.is_valid:
            return self._manual(reason="invalid_parse")

        # 강화된 exact 단축 — dose_amount 유무 무관 (Gate A+)
        fast = await self._exact_single.search_single(parsed)
        if fast is not None:
            return MatchResult(
                item=None,
                stage="exact_fast",
                final_score=1.0,
                decision=MatchDecision(
                    type=MatchDecisionType.AUTO,
                    primary=fast,
                    options=[fast],
                    reason="exact_fast",
                ),
            )

        fused = await self._run_rrf(parsed)
        if not fused:
            return self._manual(reason="no_match")

        ranked = self._reranker.rerank(parsed, fused[:_RERANK_TOP_N])
        if self._bge_reranker is not None:
            ranked = self._bge_reranker.rerank(parsed.raw, ranked)
        else:
            _apply_sigmoid(ranked)
        decision = self._decider.decide(parsed, ranked)
        return MatchResult(
            item=None,
            stage="rrf",
            final_score=ranked[0].final_score,
            decision=decision,
        )

    async def _run_rrf(self, parsed: ParsedItem) -> list[Candidate]:
        results = await asyncio.gather(
            *(r.search(parsed) for r in self._retrievers.values())
        )
        retriever_results = dict(zip(self._retrievers.keys(), results))
        return fuse_rrf(retriever_results, k=RRF_K)

    @staticmethod
    def _manual(reason: str) -> MatchResult:
        return MatchResult(
            item=None,
            stage="rrf",
            decision=MatchDecision(
                type=MatchDecisionType.MANUAL,
                primary=None,
                options=[],
                reason=reason,
            ),
        )
