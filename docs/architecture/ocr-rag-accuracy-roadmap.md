# OCR + RAG 약물 조회 정확도 — 현재 구조 + 개선 로드맵

> 작성 2026-06-06. CTO 결정 — 사용자 우선순위 = "GT 100건 + RAGAS 자동화" (옵션 1) + 과정 기록.
>
> 본 문서는 (a) **현재 적용된 6-stage cascade 정리**, (b) **개선 가능 방법 카탈로그**, (c) **우선순위별 진행 계획** 3 파트로 구성됨. 면접/포폴 narrative + 향후 T-AI-* spec 의 reference 로 활용.

---

## 1. 현재 구조 — 6-stage cascade

```
[처방전 사진]
    ↓
Stage 1: OCR Parser (Gemini 2.5 Flash Vision)
    → 약 이름 / 용량 / 용법 / 일수 추출
    파일: app/rag/ocr/parser.py + vision.py
    ↓ raw text "타이레놀 5mg"
Stage 2: Exact Dictionary Lookup
    → drugs.name 정확 일치 (대소문자/공백 정규화)
    파일: app/rag/ocr/drug_search.py + normalizer.py
    ↓ miss
Stage 3: Fuzzy Lexical
    - jamotools (한국어 자모 분해)
    - python-Levenshtein (jamo edit distance)
    - pg_trgm GIN (trigram similarity)
    파일: app/rag/ocr/fuzzy_search.py
    ↓
Stage 4: Dense Semantic (pgvector)
    - text-embedding-3-small (768d)
    - ivfflat cosine (lists=100)
    - top-K 동적
    파일: app/rag/pgvector_retriever.py
    ↓
Stage 5: RRF + Domain Reranker
    - k = 60
    - weights [vector 0.6, lexical 0.4]
    - Domain Reranker
    파일: app/rag/ocr/rrf.py + rrf_matcher.py + reranker.py
    ↓
Stage 6: 3-Tier MatchDecision
    - ABS_THRESHOLD = 0.70
    - MARGIN_THRESHOLD = 0.05 (1위 - 2위 차이)
    - DOSE_MATCH_BONUS = +0.5 (mg 일치)
    - JAMO_PENALTY = -0.05 (자모 불일치)
    - ALIAS_USER_BONUS = +0.03 (사용자 학습 별칭)
    파일: app/rag/ocr/decider.py + matcher.py
    ↓
[AUTO_ACCEPT / SUGGEST / MANUAL_REVIEW]
```

### 1.1 부가 인프라

| 컴포넌트 | 역할 |
|---|---|
| `drug_master_alias` 테이블 | 별칭 사전 (T013, 일반명/제품명/약어) |
| `drug_embeddings` (pgvector) | 768d 의미 임베딩 |
| `pg_trgm` (GIN index) | trigram lexical search |
| Redis 캐시 | drug 검색 (1h TTL) + 이미지 SHA-256 hash |
| Domain Reranker | RRF 결과 재가중 |
| 사용자 confirm → alias 학습 | UI confirm 시 자동 alias 추가 |
| DDI 검증 | 처방전 등록 시 약 쌍별 병용금기 검증 |

### 1.2 임계치 / 가중치 catalog

| 상수 | 값 | 의미 |
|---|---|---|
| `ABS_THRESHOLD` | 0.70 | 절대 confidence 통과 임계 |
| `MARGIN_THRESHOLD` | 0.05 | 1위와 2위 score 차이 (margin 작으면 ambiguous) |
| `DOSE_MATCH_BONUS` | +0.5 | mg 일치 시 가산 |
| `JAMO_PENALTY` | -0.05 | 자모 불일치 1 마다 감산 |
| `ALIAS_USER_BONUS` | +0.03 | 사용자 confirm 학습 별칭 hit 시 가산 |
| `RRF_K` | 60 | Reciprocal Rank Fusion 분모 |
| `HYBRID_WEIGHTS` | [0.6, 0.4] | [vector, lexical] |
| `FAITHFULNESS_MIN` | 0.95 | RAGAS Faithfulness 최소 (이하 fallback) |
| `OCR_MIN_CONFIDENCE` | 0.70 | OCR raw confidence (이하 사용자 확인 단계) |
| `EMBED_CACHE_THRESHOLD` | 0.92 | 챗 임베딩 유사도 캐시 hit |

### 1.3 데이터 규모

- **drugs (식약처)**: 47,021 건
- **drug_master_alias**: 67,653 건
- **drug_interactions (DDI)**: 656,774 건 (양방향 검증 asymmetric 0)
- **drug_embeddings**: 4,736 건 (T011 OpenAI 임베딩, T014 재적재 후 일부)

### 1.4 측정 (현재 미구축 — 본 로드맵의 대상)

- **RAGAS 평가 자동화** ❌
- **실 처방전 GT (Ground Truth) 데이터셋** ❌
- **A/B 테스트 인프라** ❌
- **단계별 hit rate 로깅** ❌ (대신 단위 테스트만)

---

## 2. 개선 방법 카탈로그 (단기/중기/장기)

### 2.1 단기 (1주 이내, Quick Win)

| # | 방법 | 효과 (예상) | 비용 |
|---|---|---|---|
| Q1 | **GT 데이터셋 100건 구축** | 모든 후속 측정 base | 4~6h 수작업 |
| Q2 | **RAGAS 평가 자동화** (Faithfulness / Context Recall / MRR / Hit Rate@k) | 정량 측정 + 회귀 방지 | 1d |
| Q3 | **BGE-Reranker-v2-m3 도입** (Stage 5, 무료 OSS, 한국어 강함) | MRR +15~25% | 0.5d |
| Q4 | **임계치 튜닝** (ABS_THRESHOLD/MARGIN) — GT 기반 ROC | Precision/Recall 최적화 | 0.5d (Q1 후) |
| Q5 | **HNSW index 전환** (현재 ivfflat lists=100) | 정확도 +3~7% + 속도 ↑ | 0.5d |

→ **Q1 + Q2 가 모든 후속 작업의 prerequisite** (객관적 측정 없이는 개선 효과 검증 불가)

### 2.2 중기 (1~2주)

| # | 방법 | 효과 |
|---|---|---|
| M1 | **Cross-encoder 정밀 매칭** (top-N=10 candidates 정밀 비교) | MRR +20~30%, latency 약간 ↑ |
| M2 | **Query expansion — 약 동의어 자동 확장 LLM** | Recall +10~15% |
| M3 | **별칭 사전 확장** — 의약품 약어/상품명/oral/topical mapping bulk import | 일반명 hit ↑ |
| M4 | **임베딩 모델 비교** — `text-embedding-3-large` (3072) vs `multilingual-e5-large` (1024, OSS 한국어 ↑) | Recall +5~10% |
| M5 | **OCR 측 강화** — Gemini 2.5 Pro 비교 + few-shot 처방전 5~10 예시 | Stage 1 raw +5~15% |
| M6 | **이미지 전처리** — OpenCV 회전/대비/skew correction | OCR raw +5~10% |
| M7 | **Hybrid weights tuning** — 0.6/0.4 → grid search (GT 기반) | 도메인 최적 비율 |

### 2.3 장기 (1개월+)

| # | 방법 | 효과 |
|---|---|---|
| L1 | **HITL 학습 루프 강화** — 사용자 confirm → alias DB 자동 누적 → 시간 따라 정확도 ↑ | 개인화 + 누적 학습 |
| L2 | **다중 모델 ensemble** (Gemini + Claude Vision) — 다중 의견 voting | 추출 정확도 ↑, 비용 ↑ |
| L3 | **Domain-tuned embedding fine-tune** — 한국어 의약품 도메인 specific | Recall ↑↑ |
| L4 | **Hierarchical RAG** — drug + interaction + dosage + indication chunk 분리 | 챗봇 응답 품질 ↑ |
| L5 | **LLM-as-a-Judge** — Gemini Flash 가 top-3 후보 비교 평가 (cost-aware sampling) | top-1 정확도 ↑ |
| L6 | **Negative sampling** — 같은 분류 다른 성분 약을 hard negative 로 dense retrieval 학습 | Recall ↑ |
| L7 | **Multi-page prescription stitching** — 약봉투 여러 장 + 중복 dedup | UX ↑ |
| L8 | **Confidence calibration** — per-drug calibration table (와파린/와르파린 등 자주 혼동) | False positive ↓ |
| L9 | **OCR + DDI + 환자 정보 cross-validation** — 80세 노인 + 출혈제 처방 시 의심 alert | 의료 안전 ↑ |
| L10 | **Dose normalization layer** — "500mg" / "0.5g" / "1정" / "한 알" 정규화 | mg 일치 검증 robust ↑ |

---

## 3. Stage 별 구체적 개선 안

### Stage 1 (OCR Parser) — Gemini 정확도
| 옵션 | 비용 | 정확도 ↑ |
|---|---|---|
| Few-shot (처방전 5~10 예시) | $0 | +5~15% |
| Gemini 2.5 Flash → **Pro** | +60% | +10~20% |
| 다중 모델 ensemble (Flash + Claude Haiku) | +50% | +15~25% |
| 이미지 전처리 (OpenCV 회전/대비) | $0 | +5~10% |

### Stage 3 (Fuzzy Lexical) — 한국어 강화
| 옵션 | 효과 |
|---|---|
| 자모 + 음운 변동 (받침 → 다음 초성 결합) 매칭 | 한국어 특수 case ↑ |
| 영문 약어 → 한글 transliteration table | "ACE inhibitor" → "에이스 억제제" |
| Okapi BM25 정밀 구현 (현재 pg_trgm) | 길이 정규화 ↑ |

### Stage 4 (Dense Semantic) — 임베딩 + Index
| 옵션 | 효과 |
|---|---|
| `text-embedding-3-small` 768 → `text-embedding-3-large` 3072 | Recall +5~10%, 비용 +6x |
| `multilingual-e5-large` (1024) — 한국어 특화 OSS | 한국어 약명 ↑, 비용 $0 |
| `intfloat/multilingual-e5-instruct` — instruction-tuned | 의약품 도메인 prompt 가능 |
| ivfflat → **HNSW** index | 정확도 +3~7%, 메모리 ↑ |

### Stage 5 (RRF + Reranker)
| 옵션 | 효과 |
|---|---|
| **BGE-Reranker-v2-m3** (한국어 강함, OSS) | MRR +15~25% |
| **Cohere Reranker** (multilingual) | MRR +20~30%, $5/1K |
| Domain Reranker 강화 (의약품 분류 가중) | 동음이의 약 disambiguate |
| RRF k 튜닝 (60 → 50/40 실측) | top-1 ↑ |

### Stage 6 (MatchDecision)
| 옵션 | 효과 |
|---|---|
| ROC curve 로 ABS_THRESHOLD 최적 | FP ↓ + Recall 유지 |
| DOSE_MATCH_BONUS 동적 (mg + 단위 +0.7) | 동명이 약 disambiguate |
| Confidence 산출 — softmax scaling | UI 표시 정확도 ↑ |

---

## 4. 진행 계획 (CTO 결정 — 사용자 우선순위 1 선택)

### Phase A — 측정 기반 구축 (즉시, 1주)

```
T-AI-OCR-RAG-EVAL-GT  ← 본 로드맵 첫 번째 spec
  ├ A1. GT 데이터셋 100건 구축 (실 처방전 또는 합성)
  │   - 입력: 처방전 이미지 (S3 placeholder + 합성)
  │   - 정답: 약 이름 (KD code) + mg + 용법 + 일수
  │   - 형식: tests/eval/gt/prescriptions.jsonl
  │
  ├ A2. RAGAS 평가 인프라
  │   - Faithfulness / Context Recall / MRR / Hit Rate@1,5,10
  │   - pytest -m eval (단위와 분리)
  │   - 결과 reports/eval/{date}.json (시계열)
  │
  ├ A3. Stage 별 hit rate 로깅 (관측성)
  │   - decision logs: which stage decided + score
  │   - 향후 ablation study base
  │
  └ A4. 현재 baseline 측정 + 문서화
      → reports/eval/baseline_2026-06-06.md (포폴/면접 자료)
```

### Phase B — Quick Wins (1주 이내, A 후)

```
T-AI-RAG-RERANKER       — BGE-Reranker-v2-m3 (Q3)
T-AI-RAG-HNSW           — ivfflat → HNSW (Q5)
T-AI-RAG-THRESHOLD-TUNE — ABS/MARGIN 임계치 ROC 기반 (Q4, A 후)
```

각 spec 마다 RAGAS 측정 before/after 비교 의무 → 면접/포폴 narrative 강화.

### Phase C — 정확도 + 데이터 확장 (1~2주)

```
T-AI-OCR-FEWSHOT        — Few-shot 처방전 5~10 예시 (M5)
T-AI-OCR-PREPROCESS     — OpenCV 회전/대비/skew (M6)
T-AI-RAG-CROSS-ENCODER  — top-N 정밀 매칭 (M1)
T-AI-RAG-QUERY-EXPAND   — 동의어 자동 확장 LLM (M2)
T-AI-DRUG-ALIAS-BULK    — 별칭 사전 확장 (M3)
T-AI-RAG-EMBED-COMPARE  — text-embedding-3-large vs multilingual-e5-large 비교 (M4)
```

### Phase D — 장기 (Phase 2~3 검토)

L1~L10 항목. 면접 narrative 차원에서 "검토 후 미도입" 항목도 의도적으로 기록 — 오버엔지니어링 회피 근거.

---

## 5. 면접/포폴 narrative

| narrative | 측정 가능 자료 (Phase A 후 생성) |
|---|---|
| "OCR + RAG 6-stage cascade 직접 설계" | stage 별 hit rate + ablation |
| "한국어 약명 fuzzy 매칭 — jamotools + Levenshtein" | 자모 거리 0/1/2 별 정확도 |
| "Hybrid Retrieval — vector + BM25 + RRF" | vector only vs hybrid Recall@10 |
| "Reranker 도입으로 MRR +20%" | before/after RAGAS |
| "HITL alias 학습 — 사용자 confirm → alias 누적 → 정확도 자동 ↑" | 시간 흐름 alias 증가 + MRR 추세 |
| "측정 없는 개선은 미신" — RAGAS 자동화 도입 의사결정 | reports/eval/ 시계열 |

→ 면접관 질문 "왜 0.70 임계치인가?" 에 ROC curve 로 답변 가능 (현재는 직관 → A4 후 객관)

---

## 6. 제약 (cost-aware + medical-safety 룰 준수)

- LLM 모델: gemini-2.5-flash (OCR/chat), GPT-4o 금지
- 임베딩: text-embedding-3-small 또는 multilingual-e5 OSS 우선
- Cohere Reranker 는 비용 검토 후 결정 (BGE OSS 우선)
- Faithfulness < 0.95 시 fallback ("약사 또는 의사와 상담")
- OCR confidence < 0.70 시 사용자 확인 단계
- GT 데이터셋의 처방전 이미지에 환자 식별 정보 X (합성 또는 마스킹)

---

## 7. 참조

- `.claude/rules/python/langchain.md` — RAGAS / Hybrid Retrieval 룰
- `.claude/rules/python/fastapi.md` — Async / LLM 호출 룰
- `.claude/rules/sql/postgres.md` — pgvector ivfflat / HNSW
- `.claude/rules/common/medical-safety.md` — 신뢰도 임계치 + fallback
- `.claude/rules/common/cost-aware.md` — 모델 라우팅
- `.claude/agents/rag-curator.md`
- `.claude/agents/prescription-ocr-expert.md`
- `.claude/agents/medical-domain-validator.md`
- `back/ai_server/app/rag/ocr/` — 현재 구현
- `.cmux/specs/T-AI-OCR-RAG-EVAL-GT.md` — Phase A 첫 번째 spec (본 로드맵 후속)

---

## 변경 이력

| 날짜 | 변경 |
|---|---|
| 2026-06-06 | 초안 — 사용자 우선순위 결정 (옵션 1) 기반 |
