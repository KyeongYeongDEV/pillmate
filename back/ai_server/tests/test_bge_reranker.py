"""
BGE Reranker TDD — RED → GREEN cycle.

BgeRerankerAdapter: cross-encoder 기반 재정렬 (BAAI/bge-reranker-v2-m3).
모델 로드는 lazy, 테스트에서는 mock model 주입.
"""
from __future__ import annotations

import jamotools
import pytest

from app.rag.ocr.rrf import Candidate
from app.rag.ocr.reranker import BgeRerankerAdapter


def _cand(
    item_seq: str,
    name: str,
    rrf_score: float = 0.1,
    final_score: float = 0.1,
) -> Candidate:
    return Candidate(
        item_seq=item_seq,
        name=name,
        dose_amount=None,
        dose_unit=None,
        form=None,
        alias_source=None,
        name_jamo=jamotools.split_syllables(name),
        rrf_score=rrf_score,
        final_score=final_score,
    )


class _MockBgeModel:
    """BGE 모델 Mock — name 기준 결정적 점수 반환."""

    def __init__(self, scores_by_name: dict[str, float]) -> None:
        self._scores = scores_by_name

    def compute_score(self, pairs: list[list[str]], normalize: bool = True) -> list[float]:
        return [self._scores.get(pair[1], 0.0) for pair in pairs]


class TestBgeRerankerAdapter:
    def _adapter_with_mock(self, scores_by_name: dict[str, float]) -> BgeRerankerAdapter:
        adapter = BgeRerankerAdapter()
        adapter._model = _MockBgeModel(scores_by_name)
        return adapter

    def test_reranks_correct_drug_first_by_bge_score(self):
        """BGE 점수가 높은 약이 최종 1위로 정렬돼야 한다."""
        adapter = self._adapter_with_mock(
            {
                "타이레놀정500밀리그램": 0.95,
                "아스피린정100mg": 0.05,
            }
        )
        wrong = _cand("SEQ_WRONG", "아스피린정100mg", rrf_score=0.3, final_score=0.3)
        right = _cand("SEQ_RIGHT", "타이레놀정500밀리그램", rrf_score=0.2, final_score=0.2)

        ranked = adapter.rerank("타이레놀 500", [wrong, right])

        assert ranked[0].item_seq == "SEQ_RIGHT", (
            f"BGE 점수 기준 SEQ_RIGHT(타이레놀)이 1위여야 하지만 {ranked[0].item_seq!r} 가 1위"
        )

    def test_blends_domain_and_bge_score(self):
        """final_score = domain_score * DOMAIN_WEIGHT + bge_score * BGE_WEIGHT."""
        from app.rag.ocr.reranker import BGE_WEIGHT, DOMAIN_WEIGHT

        adapter = self._adapter_with_mock({"약정10mg": 0.8})
        cand = _cand("SEQ001", "약정10mg", final_score=0.1)

        result = adapter.rerank("약 10mg", [cand])

        expected = 0.1 * DOMAIN_WEIGHT + 0.8 * BGE_WEIGHT
        assert abs(result[0].final_score - expected) < 1e-6, (
            f"blend 수식 오차: expected {expected:.4f}, got {result[0].final_score:.4f}"
        )

    def test_returns_empty_on_empty_input(self):
        """빈 candidates 입력 시 빈 리스트 반환."""
        adapter = self._adapter_with_mock({})
        assert adapter.rerank("query", []) == []

    def test_only_top_k_processed_by_bge(self):
        """BGE는 상위 BGE_TOP_K(10)개만 처리한다."""
        from app.rag.ocr.reranker import BGE_TOP_K

        call_count = [0]

        class CountingModel:
            def compute_score(self, pairs, normalize=True):
                call_count[0] += len(pairs)
                return [0.5] * len(pairs)

        adapter = BgeRerankerAdapter()
        adapter._model = CountingModel()

        candidates = [
            _cand(f"SEQ{i}", f"약{i}정", rrf_score=0.1, final_score=0.1)
            for i in range(15)
        ]
        adapter.rerank("query", candidates)

        assert call_count[0] == BGE_TOP_K, (
            f"BGE TOP-K({BGE_TOP_K})개만 처리해야 하지만 {call_count[0]}개 처리됨"
        )

    def test_tail_candidates_preserved_after_top_k(self):
        """TOP-K 이후 candidates는 final_score 변경 없이 뒤에 붙어야 한다."""
        from app.rag.ocr.reranker import BGE_TOP_K

        adapter = self._adapter_with_mock({"약정": 0.5})
        top = [_cand(f"TOP{i}", "약정", final_score=0.5) for i in range(BGE_TOP_K)]
        tail = [_cand("TAIL", "후보약정", final_score=0.1)]

        result = adapter.rerank("약", top + tail)

        assert result[-1].item_seq == "TAIL", "TAIL 후보는 끝에 위치해야 한다"
        assert result[-1].final_score == 0.1, "TAIL 후보 final_score는 불변이어야 한다"
