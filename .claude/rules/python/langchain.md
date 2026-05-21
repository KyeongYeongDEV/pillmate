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

## Retriever

- pgvector + BM25 Hybrid
- Reciprocal Rank Fusion (RRF, k=60)
- Top-K는 dynamic (질의 명확도 기반)

```python
hybrid_retriever = EnsembleRetriever(
    retrievers=[vector_retriever, bm25_retriever],
    weights=[0.6, 0.4],
)
```

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
