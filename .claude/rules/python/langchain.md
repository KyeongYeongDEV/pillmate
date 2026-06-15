---
name: langchain
description: LangChain 사용 규칙 — RAG 체인 작성, 프롬프트 관리
---

# LangChain Rules

## 버전

- langchain 0.2+
- langchain-google-genai
- langchain-postgres (pgvector)

## 체인 구성

```python
chain = (
    {"context": retriever, "question": RunnablePassthrough()}
    | prompt
    | llm
    | parser
)
```

- 항상 `RunnableSequence` (`|` 연산자)
- 단계별 명확히 분리

## Retriever (실제 구현 — 2026-06-15 정합)

> 정직성 룰: 본 절은 현재 코드와 일치해야 한다. 과거 'pgvector+BM25 EnsembleRetriever(0.6/0.4)'
> 룰은 실제 구현과 달라 폐기. 아래가 실제 운영 경로다.

### OCR 약품명 매칭 — `RrfMatcher` (운영 경로, `rrf_factory.build_rrf_matcher_inner`)

- LangChain `EnsembleRetriever`/pgvector·BM25 아님. asyncpg 기반 자체 `MultiRetrieverPort` 6종:
  - `ExactIlikeAdapter` (exact_fast 단축), `IlikeMultiAdapter`, `TrigramMultiAdapter`(pg_trgm+jamo),
    `TokenIlikeMultiAdapter`, `PrefixRelaxMultiAdapter`, `IngredientMultiAdapter`
- 융합: Reciprocal Rank Fusion (RRF, k=60) → `DomainReranker` → `BgeRerankerAdapter`(cross-encoder) → `MatchDecider`(ABS_THRESHOLD=0.70)
- 임베딩/벡터 검색은 **OCR 매칭 경로에 미사용** (pgvector dense 는 아래 Chat RAG 전용)

### Chat RAG — pgvector dense 단독 (현재 출시 범위 미포함)

- `PgVectorRetriever` + 임베딩(dense) 단독 검색. BM25/Ensemble 하이브리드 아님.
- 출시 스코프 외 — 활성화 시 본 절 갱신 필수.

### 향후 hybrid 도입 시 재검토

- pgvector + BM25 `EnsembleRetriever`(가중 RRF) 하이브리드는 **미도입**. 구체적 정확도 격차(운영 문제)
  근거가 생길 때 도입하고 본 룰을 다시 개정한다 (no-overengineering).

## 프롬프트

- 프롬프트는 `app/rag/prompts/` 디렉터리에 분리
- Jinja-style 변수 사용
- 시스템 프롬프트에 출처 강제 지시 포함

```
시스템: "당신은 의료 정보 답변자입니다.
        제공된 [컨텍스트]에 명시된 정보만 답변하세요.
        모든 약 정보는 '식약처' 출처를 답변에 포함해야 합니다.
        컨텍스트에 없는 정보는 '확인할 수 없습니다'라고 답하세요."
```

## 응답 파싱

- `PydanticOutputParser`로 스키마 강제
- 파싱 실패 시 재시도 (Outputfixer)

## 평가

- RAGAS로 정기 평가 (skills/rag-eval)
- Faithfulness, Context Recall, MRR

## 캐싱

- `langchain.cache.RedisCache` 활성화
- FAQ 임베딩 유사도 캐싱 (임계치 0.92)

## 금지

- 출처 검증 없이 응답 반환
- 시스템 프롬프트 동적 변경 (캐시 무효화 + 재현성 문제)
- 컨텍스트에 환자 식별 정보 포함

## 참조

- `agents/rag-curator.md`
- `agents/medical-domain-validator.md`
- `rules/common/medical-safety.md`
