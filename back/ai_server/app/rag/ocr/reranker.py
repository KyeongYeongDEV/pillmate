from __future__ import annotations

import Levenshtein

from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.rrf import Candidate

try:
    from FlagEmbedding import FlagReranker as _FlagReranker
    _FLAG_AVAILABLE = True
except ImportError:
    _FlagReranker = None  # type: ignore[assignment,misc]
    _FLAG_AVAILABLE = False

BGE_MODEL_ID = "BAAI/bge-reranker-v2-m3"
BGE_TOP_K = 10
BGE_WEIGHT = 0.7
DOMAIN_WEIGHT = 0.3

DOSE_MATCH_BONUS: float = 0.5
DOSE_MISMATCH_PENALTY: float = -0.5
FORM_MATCH_BONUS: float = 0.2
ALIAS_PRODUCT_BONUS: float = 0.1
ALIAS_INGREDIENT_BONUS: float = 0.05
ALIAS_USER_BONUS: float = 0.03
JAMO_PENALTY_PER_CHAR: float = 0.05


class DomainReranker:
    def rerank(self, parsed: ParsedItem, candidates: list[Candidate]) -> list[Candidate]:
        for c in candidates:
            c.final_score = (
                c.rrf_score
                + self._dose_score(parsed, c)
                + self._form_score(parsed, c)
                + self._alias_score(c)
                + self._jamo_score(parsed, c)
            )
        return sorted(candidates, key=lambda x: -x.final_score)

    def _dose_score(self, parsed: ParsedItem, c: Candidate) -> float:
        if not parsed.dose_amount or not c.dose_amount:
            return 0.0
        if parsed.dose_amount == c.dose_amount and parsed.dose_unit == c.dose_unit:
            return DOSE_MATCH_BONUS
        return DOSE_MISMATCH_PENALTY

    def _form_score(self, parsed: ParsedItem, c: Candidate) -> float:
        if parsed.form and parsed.form == c.form:
            return FORM_MATCH_BONUS
        return 0.0

    def _alias_score(self, c: Candidate) -> float:
        if c.alias_source == "product":
            return ALIAS_PRODUCT_BONUS
        if c.alias_source == "ingredient":
            return ALIAS_INGREDIENT_BONUS
        if c.alias_source == "user":
            return ALIAS_USER_BONUS
        return 0.0

    def _jamo_score(self, parsed: ParsedItem, c: Candidate) -> float:
        if not parsed.name_jamo or not c.name_jamo:
            return 0.0
        distance = Levenshtein.distance(parsed.name_jamo, c.name_jamo)
        return -distance * JAMO_PENALTY_PER_CHAR


class BgeRerankerAdapter:
    """BAAI/bge-reranker-v2-m3 cross-encoder 재정렬 어댑터.

    DomainReranker 이후 Stage 5 으로 호출된다.
    모델은 lazy load — 첫 rerank() 호출 시 다운로드.
    transformers ≥ 4.47 에서 XLMRobertaTokenizer.prepare_for_model 제거 시
    _degraded=True 로 설정 후 DomainReranker 결과를 그대로 반환한다.
    """

    def __init__(self) -> None:
        self._model = None
        self._degraded: bool = False

    def _load(self):
        if self._model is None:
            if not _FLAG_AVAILABLE:
                raise ImportError(
                    "FlagEmbedding not installed. Run: pip install FlagEmbedding>=1.2"
                )
            self._model = _FlagReranker(
                BGE_MODEL_ID,
                use_fp16=True,
                normalize=True,
            )
        return self._model

    def warmup(self) -> None:
        # startup 시 dummy pair 로 모델 사전 로드 → 첫 요청 -60초 (cold start 제거).
        try:
            model = self._load()
            model.compute_score([["아모디핀정 5mg", "아모디핀정 5mg"]], normalize=True)
        except Exception as exc:
            self._mark_degraded()
            raise exc

    def _mark_degraded(self) -> None:
        # degraded 확정 시 모델 참조 해제 — 기여 없는 ~600MB 를 GC 대상으로 (재로드는 _degraded 가 차단)
        self._degraded = True
        self._model = None

    def rerank(self, query: str, candidates: list[Candidate]) -> list[Candidate]:
        """BGE cross-encoder 로 상위 BGE_TOP_K 후보를 재정렬한다.

        final_score = domain_score * DOMAIN_WEIGHT + bge_score * BGE_WEIGHT

        AttributeError (transformers API 변경) 발생 시 _degraded=True 설정 후
        입력 candidates 를 그대로 반환 (DomainReranker-only fallback).
        """
        if not candidates or self._degraded:
            return candidates
        top = candidates[:BGE_TOP_K]
        rest = candidates[BGE_TOP_K:]
        try:
            model = self._load()
            pairs = [[query, c.name] for c in top]
            bge_scores = model.compute_score(pairs, normalize=True)
            for c, bge_score in zip(top, bge_scores):
                c.final_score = (
                    c.final_score * DOMAIN_WEIGHT + float(bge_score) * BGE_WEIGHT
                )
            return sorted(top, key=lambda x: -x.final_score) + rest
        except Exception as exc:
            import logging
            logging.getLogger(__name__).warning(
                "BGE rerank 실패 → DomainReranker only fallback: %s", exc
            )
            self._mark_degraded()
            return candidates
