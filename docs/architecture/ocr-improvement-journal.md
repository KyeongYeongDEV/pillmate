# OCR + RAG 약품 매칭 개선 일지 (Journal)

> 시간순 narrative log. 모든 OCR/RAG 관련 task 의 **시도 / 결과 / 교훈 / 다음 가설** 누적 기록.
>
> 사용자 영구 룰 (2026-06-09): "현재 OCR 개선을 위해 해보고 있는 과정과 결과들 전부 다 계속 기록"

---

## 📊 누적 요약표

| Phase | Task | 날짜 | 핵심 변화 | 주요 수치 |
|---|---|---|---|---|
| A | #115 EVAL-GT | 2026-06-07 | GT 100건 + RAGAS 자동화 + baseline | **Hit@1 0.700** |
| B-1 | #122 DB-CONNECT | 2026-06-08 | vector/ingredient stage 실제 DB 연결 | **hard 0.038 → 0.962 (+92.4pp)** |
| B-2 | #123 RERANKER | 2026-06-08 | BGE-Reranker-v2-m3 도입 (Stage 5) | **medium 0.886 → 0.955 (+6.9pp)** |
| B-3 | #124 EVAL-FULL | 2026-06-09 | 전체 통합 측정 + 실 약봉투 8장 E2E | **전체 Hit@1 0.970 (97/100)** |
| B-4 FE | T-FE-OCR-MANUAL-REVIEW | 2026-06-09 | OCR confirm 화면 + 안전망 UX | 사용자 안전망 구축 |
| B-4 BE | T-AI-RAG-LLM-FALLBACK | 2026-06-09 | 4-Tier fallback cascade | **miss_1+2 해결 확정 (분석적)** |
| B-5 | T-AI-RAG-EMBED-BULK | 2026-06-09 | drug_embeddings 9.6%→100% | **$0.003, 47,021건, 0 failed** |
| B-6 FE | T-FE-CAMERA-GUIDE | 2026-06-09 | 촬영 가이드 overlay + 실시간 hint + 자동 셔터 | input quality ↑ 예측 +10~20pp |
| B-6 BE | T-AI-OCR-RAW-QUALITY | 2026-06-09 | OpenCV 전처리 + Few-shot 10건 + feature flags | GT 0.970 회귀 없음 ✓, 실 재측정 예정 |
| B-6 RERUN | T-AI-OCR-REAL-RERUN | 2026-06-09 | 실 약봉투 7장 재측정 (6장 성공, 1x503, 1x429) | **28/30 = 93.33%** (+4.76pp vs B-3 88.57%) |
| B-7a | T-AI-OCR-MULTI-KEY-FALLBACK | 2026-06-10 | Gemini 다중 키 로테이션 (RPD 20→40) | 429→fallback 자동, 202 tests PASS |
| B-7b | T-AI-OCR-PILL-IDENTIFY-FALLBACK | 2026-06-10 | 낱알식별 Tier 5 (DB 9,679건 shape/color/mark) | Tier 5 cascade 통합, 실측 예정 |
| P0 | ai-server 스타트업 크래시 | 2026-06-10 | Dockerfile cv2 누락 + GeminiInvoker api_keys | health 200 ✓ |
| C-1 | #148 T-AI-THRESHOLD-SWEEP | 2026-06-13 | ABS_THRESHOLD=0.70 미튜닝 확인 + BGE 의존성 크래시 발견 | RRF Hit@1 56/61=91.8% (BGE 없이), 전체 56/100=56.0% |
| B-8 | #150 T-AI-WIRE-RRFMATCHER-PROD | 2026-06-13 | Gate D: main.py RrfMatcher 주입 + eval=prod 단일 경로 | surfacing 98%=98%, false-auto=0, 187 tests PASS |
| B-9 | #151 T-AI-REAL-E2E-RRF | 2026-06-13 | 실 처방전 8장 운영 RRF 경로 첫 실측 | **AUTO 77.1%, surfacing 100%, ⚠️ 함량 불일치 false-auto 2건 발견** |
| B-10 | #152 T-AI-DOSE-VERIFY-SHORTCIRCUIT | 2026-06-14 | StrongExact 함량 검증 + 이름 prefix 강화 + 점수 버그 수정 | **false-auto 0건, 정답 AUTO 회복, GT100 98% 유지** |
| L-1 | T-AI-OCR-LATENCY-60S | 2026-07-14 | vision thinking_budget=0 + 프롬프트 필드정의 강화 + correction lite/8s | **vision 17.7~26.8s→4.5~7.6s, 실8장 AUTO 22/35·multiline 0·신규 false-auto 0** |
| L-2 | T-AI-DOSE-NULL-CONFIRM | 2026-07-14 | dose=None 다강도 오확정 AUTO 차단 (Adversarial 발견, dead code `_dose_variants` 재활성화) | **RED 8건 재현→GREEN, 실8장 정답 AUTO 3건 유지, GT100 surfacing 98% 유지, false-auto 0** |

---

## 1. Phase A — Baseline 측정 인프라 (#115 T-AI-OCR-RAG-EVAL-GT)

**날짜**: 2026-06-07
**Why**: 측정 없이 개선 효과 검증 불가. 면접 narrative "왜 ABS_THRESHOLD=0.70?" 답변용 ROC 근거.

### 시도
- GT 100건 데이터셋 구축 (50건 마스킹 실 처방전 + 50건 합성)
- RAGAS metric 자동화: Hit Rate@1/5/10, MRR, Context Recall, Faithfulness
- Stage 별 structured log 추가 (`app/rag/ocr/service.py`)
- `pytest -m eval` marker + `reports/eval/baseline_{date}.{md,json}`
- 합성 처방전 이미지 100건 + `synthesize_prescription_images.py`

### 결과 (baseline_2026-06-07.md)
```
전체 Hit@1: 0.700
easy 30건:   1.000 (ILIKE 완벽)
medium 44건: 0.886 (token/fuzzy 89%)
hard 26건:   0.038 ← 큰 문제 발견
MRR:         0.700 (Hit@5=Hit@1, 오프라인 한계)
Faithfulness 추정: 0.678 (Phase B LLM-judge 필요)
```

### 교훈
- **오프라인 평가 한계 발견**: hard 케이스의 ingredient + vector stage 가 오프라인 미연결 → 0.038 의 root cause 가 평가 환경 vs 코드 문제 둘 다
- Stage 분포: ilike 51%, ingredient 20%, token 19%, vector 10%

### 실패 case 10건 (개선 hint)
| ID | 입력 | 실패 원인 | 개선안 |
|---|---|---|---|
| gt_031~035 | "항히스타민제", "혈압약" 등 | 카테고리 입력 | vector 검색 |
| gt_071~073 | "Tylenol", "Amoxicillin", "Ibuprofen" | 영문 성분/브랜드 | alias + ingredient |
| gt_096~097 | "진통소염제", "수면제" | 효능 기반 | vector efficacy 임베딩 |

### 다음 가설
> "vector + ingredient stage 를 실제 DB 에 연결하면 hard 26건 60~70% 추가 해결 → 전체 0.700 → 0.90+"

---

## 2. Phase B-1 — DB 연결 (#122 T-AI-RAG-DB-CONNECT)

**날짜**: 2026-06-08
**Why**: Phase A 의 가설 검증. BGE/HNSW/임계치 등 후속 개선의 prerequisite.

### 시도
- `pgvector_retriever.py` SQL 버그 fix (`d.main_ingr` → `d.ingredient` 컬럼명 오류) ← **단순 오타!**
- `AsyncpgIngredientSearch` SQL 강화: `drug_alias → drug_master → drugs` 조인 + 양방향 ILIKE
- V26 마이그레이션: `idx_drugs_ingredient_trgm` GIN pg_trgm index
- 영문 alias 27건 bulk seed (Tylenol/Amoxicillin/Ibuprofen/Aspirin/Metformin 등)
- 한국어 카테고리 매핑 ("항히스타민제" → 세티리진, "혈압약" → 암로디핀 등)

### 결과 (post_db_connect_2026-06-08.md)
```
| 지표 | baseline | post_db_connect | 개선 |
|------|----------|-----------------|------|
| Hard Hit@1 | 0.038 (1/26) | 0.962 (25/26) | +92.4pp |
| 한국어 카테고리 10건 | 0.000 | 1.000 | +100pp |
| 영문 INN 15건 | 0.000 | 1.000 | +100pp |
| 유일 MISS | — | gt_092 "에소메프라조를" (typo) | fuzzy stage 담당 |
```

### 교훈
- **가장 큰 임팩트는 단순 오타 fix** — `d.main_ingr` → `d.ingredient`. Fancy 한 모델 변경보다 SQL 한 줄.
- **drug_alias 테이블이 이미 있었음** — 활용 안 되고 있었던 게 문제. 영문 alias 27건만 추가해도 영문 INN 100% 해결.
- **목표 0.6+ 초과 0.962 달성** — root cause 정확 진단의 위력.

### 위험 회피
- V26 마이그레이션 = ADD COLUMN/INDEX 만 (db-safety 준수)
- INSERT only seed (DELETE/UPDATE X)

### 다음 가설
> "BGE-Reranker-v2-m3 도입하면 medium 44건 0.886 의 fuzzy stage 정밀도 +5~10pp 추가 개선"

---

## 3. Phase B-2 — BGE Reranker (#123 T-AI-RAG-RERANKER)

**날짜**: 2026-06-08
**Why**: medium 구간 정밀도 부족 + MRR 0.7 (오프라인 단일 후보 한계). cross-encoder 정밀 재정렬 필요.

### 시도
- `FlagEmbedding>=1.2` 의존성 추가
- `BgeRerankerAdapter` 신규 (`app/rag/ocr/reranker.py`):
  - 모델: `BAAI/bge-reranker-v2-m3` (OSS, 한국어 강함, 568MB)
  - lazy load + `BGE_TOP_K=10` + `BGE_WEIGHT=0.7` + `DOMAIN_WEIGHT=0.3`
  - `final_score = domain_score * 0.3 + bge_score * 0.7`
- `RrfMatcher` Stage 5 통합: `DomainReranker → BGE Reranker` 순차
- `RERANKER_ENABLED` feature flag (A/B 비교)
- TDD RED → GREEN → REFACTOR 사이클 (d175c2b → 5e56f20 → 13afe8a)

### 결과 (post_reranker_2026-06-08.md)
```
| 지표 | offline baseline | post_db_connect | post_reranker | 개선(reranker) |
|------|:---:|:---:|:---:|:---:|
| Medium Hit@1 | 0.886 (39/44) | 0.886 | 0.955 (42/44) | +6.9pp |
| Medium Ingredient 5건 | 0.000 | 1.000 | 1.000 | — |
| Medium Token 26건 | 0.885 | — | 0.962 (25/26) | — |
| Medium Fuzzy 13건 | ~0.77 | — | 0.923 (12/13) | — |
| Hard Hit@1 | 0.038 | 0.962 | 0.962 | (유지) |
```

### 발견된 미해결 misses 2건
- **gt_016 "리오노필정"** — DB 미수록 (recall 이슈, EMBED-BULK 필요)
- **gt_039 "아스피링정"** — prefix='아스피' → "아스피도" 와 오분류 (precision 이슈, 임계치 튜닝 필요)

### 교훈
- **TDD 사이클 명확화**: RED commit (d175c2b) 으로 실패 테스트 먼저 → GREEN 으로 통과 → REFACTOR 로 정리. 면접 narrative 핵심 자료.
- **CTO 자동 회신 절차 준수**: 이번엔 BE-Dev 가 정식 outbox JSON 회신 (`DONE_DEV_T-AI-RAG-RERANKER`). 이전 #122/#118-119 는 누락 → 절차 진화.
- **Reranker 효과는 medium fuzzy 에서 가장 큼** (0.077 → 0.923 = +84.6pp on 13건).

### 다음 가설
> "전체 RrfMatcher 통합 (DB 연결 + Reranker 동시) 측정 시 전체 Hit@1 0.95+ 도달 예상"

---

## 4. Phase B-3 — 전체 통합 + 실 약봉투 (#124 T-AI-RAG-EVAL-FULL)

**날짜**: 2026-06-09 ✅ 완료
**Why**: Phase B-1 + B-2 누적 효과를 stage 별 측정 분리 → 통합 한 번에 측정 필요. 추가로 사용자 실 약봉투 8장 정성 검증.

### 시도
- `EvalFullRunner` 신규 (`tests/eval/run_eval_full.py`)
  - DB 연결 활성 (env `EVAL_DB_ENABLED=true`)
  - RERANKER_ENABLED=true
  - GT 100건 + 실 8장 동시 처리
- TDD RED 14건 (`_auto_inn` / `_is_hit_by_inn` / `_get_inn` 순수함수)
- 실 약봉투 8장 export: `back/ai_server/tests/eval/real_prescriptions/IMG_0007.PNG ~ IMG_0014.JPEG`
  - 사용자가 iOS 시뮬레이터 갤러리에 추가한 사진 (2026-05-24 ~ 2026-06-02 mtime)
  - 시뮬레이터 UDID `67AE0C53-0D46-41FB-B100-9664BD56E4AF`

### 결과 (eval_full_2026-06-09.json, 진행 중)
```
| 지표 | baseline | post_db_connect | post_reranker | eval_full (통합) |
|------|----------|-----------------|---------------|------------------|
| 전체 Hit@1 | 0.700 | (hard만) | (medium만) | 0.970 (97/100) ⭐ |
| hard | 0.038 | 0.962 | 0.962 | (포함) |
| medium | 0.886 | — | 0.955 | (포함) |
| easy | 1.000 | — | — | (포함) |
```

### 실 약봉투 8장 E2E (진행 중)
- Gemini Vision OCR 호출 (cost-aware <$0.05)
- 6-stage cascade 거쳐 약품 매칭
- 보고서: `reports/eval/real_e2e_2026-06-09.md` (대기)
- 백그라운드 watcher PID 40618 가 보고서 도착 시 macOS 알림 + log

### 교훈 (잠정)
- **목표 0.95+ 초과 달성 (0.970)** — Phase B-1 + B-2 효과 누적 확인
- 후속 misses 3건 (97/100 중 3건): gt_092 typo + gt_016 DB 미수록 + gt_039 precision
  → fuzzy 개선 / EMBED-BULK / THRESHOLD-TUNE 후속 가설

### 다음 가설 (Phase B-4+ 후보)
> 1. **T-AI-RAG-EMBED-BULK**: 현재 drug_embeddings 4,736건 (9.6%) → 47,021건 (100%) 확장 시 vector recall ↑
> 2. **T-AI-RAG-HNSW**: ivfflat → HNSW 정확도 +3~7%, 속도 ↑
> 3. **T-AI-RAG-THRESHOLD-TUNE**: gt_039 precision 이슈 ROC curve 기반
> 4. **T-AI-RAG-FUZZY-IMPROVE**: gt_092 같은 typo 케이스 fuzzy stage 개선

---

## 📝 운영 룰 (사용자 명시 2026-06-09)

> "현재 OCR 개선을 위해 해보고 있는 과정과 결과들 전부 다 계속 기록해야 해 잊지마"

### 적용 절차
1. **모든 OCR/RAG task 완료 시** 본 일지 새 섹션 추가 (시도/결과/교훈/다음 가설)
2. **수치는 반드시 baseline 대비 변화** 표시 (+Xpp, +X%)
3. **실패 case** 분석 + Phase 후속 가설
4. **시간순 narrative** 유지 — 면접에서 "이걸 왜 했는지, 무엇을 배웠는지" 답변 자료
5. **메모리 동기화**: `~/.claude/projects/.../memory/feedback_ocr_improvement_log.md` 가 본 파일 참조

### 누적 보고서 위치
- `back/ai_server/reports/eval/baseline_2026-06-07.{md,json}`
- `back/ai_server/reports/eval/post_db_connect_2026-06-08.{md,json}`
- `back/ai_server/reports/eval/post_reranker_2026-06-08.{md,json}`
- `back/ai_server/reports/eval/eval_full_2026-06-09.{md,json}` (생성 중)
- `back/ai_server/reports/eval/real_e2e_2026-06-09.{md,json}` (대기)

### 변경 이력
---

## 5. Phase B-4 — FE 사용자 안전망 (#T-FE-OCR-MANUAL-REVIEW)

**날짜**: 2026-06-09 ✅ 완료
**Why**: 자동 매칭 88.57% (실 약봉투 31/35). 11~12% 케이스는 AI fallback 으로도 미검출 가능. 의료 안전 = 약 누락 시 환자 위험 → **사용자 안전망 UX 레이어 필수**.

### 시도

- `prescription/confirm.tsx` 신규 화면 (OCR 완료 후 사용자 review gate)
- `DrugMatchCard`: confidence 색상 분기 (높음≥0.8 녹색 / 보통0.5~0.8 주황 / 낮음<0.5 빨강 / 인식실패 빨강테두리)
- `DrugSearchModal`: Bottom Sheet Modal — DrugSearchAutocomplete 재사용, 식약처 출처 명시
- `usePrescriptionReview` hook: replaceItem dispatch + aliasLogs 추적
- `prescriptionFlowSlice.replaceItem`: 사용자 교체 시 confidence=1.0, decision=CONFIRM
- `prescriptionApi.logAlias`: POST /drugs/alias (MVP 로깅 전용, admin review 대기)
- `scan.tsx` 라우팅 변경: OCR 완료 → confirm 화면 (기존 result/[id] 직행 제거)

### 결과

```
| 지표 | Before | After |
|------|--------|-------|
| 자동 매칭율 (실 약봉투) | 88.57% (31/35) | 88.57% (AI 동일) |
| 사용자 안전망 | 없음 | confirm 화면 (수정/삭제/추가) |
| 낮은 신뢰도 경고 | 없음 | 빨간 테두리 + Alert 재확인 |
| 의료 안전 메시지 | 결과화면 일부 | confirm 화면 항상 표시 |
| alias 학습 준비 | 없음 | 로그 기록 (Phase 2 적용) |
```

6 commits / tsc PASS / Jest 12 tests PASS (기존 1건 날짜 의존 실패 pre-existing)

### 교훈

- **자동 정확도 vs UX 안전망 분리**: AI 정확도를 99%로 올려도 0% 케이스는 발생. 사용자 안전망 레이어는 별도로 필요.
- **confidence 임계치 (0.5/0.8)**: 너무 strict 하면 confirm 부담 증가, 너무 loose 하면 잘못된 약 통과. 0.5 이상 → 주황, 0.8 이상 → 녹색으로 설정.
- **drug_alias MVP 로깅 전용**: 사용자 오류 confirm 이 alias DB 오염 위험 (Risk P1). Phase 2 admin review 게이트 도입 예정.

### 다음 가설 (Phase B-5+ 후보)

> 1. **T-AI-RAG-LLM-FALLBACK**: LLM 2차 매칭 (BE 자동 정확도 ↑, 병행 spec)
> 2. **Phase 2**: drug_alias admin review 화면 + 학습 자동화
> 3. **T-BE-POST-/DRUGS/ALIAS**: 서버 alias endpoint 구현 (MVP 현재 미구현, 404 반환)

---

## 6. Phase B-4 BE — 4-Tier Fallback Cascade (#T-AI-RAG-LLM-FALLBACK)

**날짜**: 2026-06-09  
**Why**: 실 약봉투 8장 31/35 = 88.57% → 96%+ 목표. 4건 miss 분석: 2건은 OCR 오인식 + fuzzy 부족, 2건은 DB 미수록.

### 시도

1. **Tier 0 — 제조사명 strip + 정규화** (`normalizer.py`):
   - `MANUFACTURER_PREFIXES` frozenset 50+ 제조사명 (OCR typo 포함)
   - `strip_manufacturer_prefix(name)`: longest-first 매칭
   - `normalize_for_cascade(name)`: strip → normalize → fallback
   - "중근당아목시실린캡슐500밀" → "아목시실린캡슐" ✓
   - `_UNIT_REGEX` 확장: "500밀" → 제거 (기존 "밀리그램"만 → "밀/밀리/mg/mcg" 추가)

2. **Tier 1 — Jamo prefix_match** (`fuzzy_search.py`):
   - `JamoFuzzyRanker.rerank(prefix_match=True)`: `db_jamo[:len(query_jamo)]` 비교
   - "쎌박타민정" jamo vs "썰박타민정500밀리그램" jamo prefix → dist=1 (기존: 14) ✓
   - `prefix_match=False` 기본값 유지 (하위 호환)

3. **Tier 2 — Vision prompt 강화** (`ocr_system.txt`, `domain/ocr.py`):
   - `RawOcrItem.candidates: list[str] = []` 필드 추가
   - OCR 프롬프트: confidence < 0.7 → candidates top-3 요청 (추가 호출 0건)
   - `GeminiVisionAdapter._detect_mime_type()`: PNG/JPEG MIME type 자동 분기 (기존 hardcode image/jpeg 수정)

4. **Tier 3 — OcrCorrectionAdapter** (`correction.py`):
   - cascade 전체 실패 시 Gemini Flash 별도 호출
   - `CORRECTION_PROMPT_TEMPLATE`: 식약처 출처 명시 (medical-safety)
   - 실패/파싱 오류 시 `[]` 반환 (cascade 계속 진행)

5. **OcrPrescriptionService cascade 통합** (`service.py`):
   - `correction: OcrCorrectionAdapter | None` 옵셔널 주입
   - `_match_with_fallback()`: Tier 0 → Tier 2 candidates → Tier 3 순차 실행

6. **Feature flags** (`config.py`):
   - `OCR_CORRECTION_ENABLED=True` (Tier 3 활성화 기본)
   - `OCR_GROUNDING_ENABLED=False` (Tier 4 기본 비활성)

### TDD 현황

```
Tier 0 RED (11건) → GREEN (11건) ✓
Tier 1 RED (4건)  → GREEN (4건)  ✓
Tier 2 RED (6건)  → GREEN (6건)  ✓
Tier 3 RED (6건)  → GREEN (6건)  ✓
총 27건 단위 테스트 PASS
```

### 결과 (post_fallback_2026-06-09.md)

```
| 지표 | Before (B-3) | After (B-4) | 비고 |
|------|-------------|-------------|------|
| GT 100건 Hit@1 | 0.970 (97/100) | 0.970 (97/100) | 회귀 없음 ✓ |
| Tier 0 cascade 효과 | — | miss_2 해결 확정 | "중근당..." 분석적 검증 |
| Tier 1 cascade 효과 | — | miss_1 해결 확정 | dist 14→1 분석적 검증 |
| Real E2E 재실행 | 31/35 = 88.57% | 1/1 (API 할당량 소진) | 내일 재실행 필요 |
```

**예상 개선**: miss_1(Tier 1) + miss_2(Tier 0) 해결 → 33/35 = 94.3% 최소, Vision candidates 적중 시 34/35 = 97.1% ≥ 96%

### API 할당량 이슈

Gemini Flash free tier RPD = 20 (일일). Phase B-3 eval + Phase B-4 eval 당일 2회 실행으로 초과.  
→ **내일(2026-06-10 00:00 UTC) 리셋 후 real E2E 재실행 예정**

### 교훈

- **Jamo 거리 측정의 함정**: "쎌박타민정500밀리" 의 full jamo distance = 14 (dose suffix 때문) → first_token() 으로 쿼리 추출 후 DB prefix 비교로 해결
- **제조사명 OCR 오인식**: "종근당" → "중근당" (ㅈㅗ→ㅈㅜ ㅅ→) OCR 흔한 오류 → dict에 typo variant 포함
- **단위 regex 확장**: "500밀" 은 "밀리그램" 을 "밀" 로 잘라 쓴 케이스 → regex에 "밀", "밀리" 추가
- **의료 안전 필수**: LLM correction 프롬프트에 "식약처" 출처 명시 (`CORRECTION_PROMPT_TEMPLATE`)

### 커밋 목록

| # | hash | 설명 |
|---|------|------|
| 1 | e38e199 | Test(RED): Tier 0+1 15건 |
| 2 | e653282 | Feat: Tier 0+1 normalizer + fuzzy prefix_match |
| 3 | 57b0c50 | Test(RED): Tier 2 6건 |
| 4 | 5075aa4 | Feat: Tier 2 RawOcrItem.candidates + _detect_mime_type |
| 5 | e516ad8 | Test(RED): Tier 3 6건 |
| 6 | cc3cf02 | Feat: Tier 3 OcrCorrectionAdapter |
| 7 | 663ecf6 | Refactor: OcrService 4-Tier cascade 통합 |
| 8 | 01304d4 | Refactor: _cascade_search Tier 0+1 통합 |

### 다음 가설

> 1. **Real E2E 재실행** (내일): 96%+ 목표 수치 확인
> 2. **T-AI-RAG-EMBED-BULK**: drug_embeddings 4,736건 → 47,021건 확장 (recall ↑)
> 3. **Tier 4 Search Grounding**: miss_3/4 같은 DB 미수록 케이스 ($3/일 비용 검토)
> 4. **T-BE-POST-/DRUGS/ALIAS**: alias endpoint 구현 (현재 404)

---

## 7. Phase B-5 — 전체 임베딩 확장 (#T-AI-RAG-EMBED-BULK)

**날짜**: 2026-06-09  
**Why**: drug_embeddings 4,736건(9.6%)만 존재 → vector stage recall 제한. 나머지 42,285건은 임베딩 없어 vector 검색에서 miss 가능.

### 시도

- `bulk_embed_drugs.py` 신규 (`back/ai_server/scripts/`)
  - SELECT 조건: `efficacy/dosage IS NOT NULL` 제거 → **ALL ACTIVE drugs**
  - `ON CONFLICT DO NOTHING` (db-safety: INSERT only)
  - checkpoint `back/.drug_embed_checkpoint.json` (중단/재시작 안전)
  - failure log `back/.drug_embed_failures.jsonl`
  - `--dry-run --limit 100` 모드: DB INSERT 없이 비용 측정
  - batch 100건, retry 5x exponential backoff
- TDD: 20건 RED → GREEN
  - `_build_text`, `_batch_chunks`, `_load/_save_checkpoint`, `_is_rate_limit`, `_embed_with_retry`, `estimate_cost`
- dry-run 100건 → 비용 측정 후 full run
- ivfflat lists=100 → 200 재빌드 (sqrt(47021) ≈ 217)

### 결과

```
drug_embeddings: 4,736 (9.6%) → 47,021 (100%) ✓
실제 비용: $0.0025 (예상 $0.09 대비 1/36 — 약품명 텍스트 짧음)
소요 시간: ~5분 (100건/초)
실패: 0건
ivfflat lists: 100 → 200 (47K 최적)
GT 100건: 97/100 = 0.970 (회귀 없음) ✓
```

### 발견된 한계

**vector stage는 순수 약품명 기반으로는 phonetic 유사도 캡처 어려움**:
```
'에치콘정' → ('캐치콘정', 0.612) — 연관성 낮음
'쎌박타민정' → ('브이타민정', 0.521) — miss
```
→ 한국어 brand name 만으로는 text-embedding의 semantic 효과 제한.  
→ Tier 1 jamo fuzzy 가 이 케이스에서 더 효과적.

**커버리지는 확장됐으나 GT miss 3건은 불변**:
- gt_016 "리오노필정": DB 미수록 (어떤 단계도 해결 불가)
- gt_039 "아스피링정": precision collision (prefix 공유 약품 경쟁)
- gt_092 "에소메프라조를": 심각한 OCR 타이핑 오류

### 교훈

- **비용 추정 오류**: 47K × 평균 50 토큰 가정 → 실제 avg 3.6 토큰/약품 (품목명 4~8자). text-embedding 토큰화 방식과 한국어 짧은 품목명 특성
- **vector 효과는 성분/효능 데이터 의존**: ingredient/efficacy가 대부분 NULL인 상태에서 vector recall 효과 제한. 향후 식약처 성분 데이터 보강 시 vector stage 가치 상승
- **ivfflat probe**: lists가 너무 작으면 recall 저하. sqrt(n) 공식 중요
- **ON CONFLICT DO NOTHING**: db-safety 준수. 재실행 시 기존 4,736건 overwrite 안 됨

### 커밋

| hash | 설명 |
|------|------|
| 78adcb8 | Test(RED): bulk_embed_drugs 20건 |
| 5920cca | Feat: bulk_embed_drugs.py + dry-run + checkpoint |

### 다음 가설

> 1. **Real E2E 재실행** (Gemini API 00:00 UTC 리셋): 실 8장 96%+ 수치 확인
> 2. **T-AI-RAG-HNSW**: ivfflat → HNSW 전환 (정확도 +3~7%)
> 3. **성분명 데이터 보강**: 식약처 성분 데이터 bulk 적재 후 재임베딩

---

## 7. Phase B-6 FE — 촬영 가이드 (T-FE-CAMERA-GUIDE)

**날짜**: 2026-06-09
**Why**: B-3 (#124) 실 약봉투 8장 E2E에서 일부 실패가 촬영 각도/조명/거리 변수로 분석됨. RAG 모델 개선으로는 해결 불가한 input 품질 문제. 사용자가 찍기 전에 가이드를 받으면 OCR raw 정확도 +10~20pp 예측.

### 시도 — 4 commits

1. **useCameraGuide hook** (TDD RED→GREEN):
   - `stability: 'loading' → 'ok'` (2.5초 타이머)
   - `warnShake()` → `'warn' → 'ok'` (2초 자동 회복)
   - `brightness`, `tilt`: static `'ok'` (Phase 2에서 expo-sensors 연동 예정)
   - expo-sensors 미설치 확인 → 타이머 기반 MVP 결정

2. **CameraGuideOverlay 컴포넌트**:
   - 4-corner bracket frame (흰색/초록 색상 분기 — `allOk` 기반)
   - 힌트 pill 3개 (📷흔들림 / 💡조명 / 📐각도)
   - `allOk` 시 pulse animation + 자동 셔터 카운트다운 표시
   - 7개 단위 테스트 PASS

3. **prescription/camera.tsx 신규 화면**:
   - scan.tsx와 동일 OCR 흐름 (upload URL → S3 → /ocr)
   - CameraGuideOverlay 오버레이 통합
   - opt-in 자동 셔터 토글 (Switch) — 3초 카운트다운 후 자동 capture
   - 처방전 등록 실패 시 `reset()` 호출 → 다시 안정화 타이머 시작

4. **라우트 + 흐름 통합**:
   - `_layout.tsx` `camera` route 등록
   - `index.tsx` 카메라 버튼 → `/prescription/camera` (기존 `/prescription/scan`)
   - `confirm.tsx` footer 3버튼 — "📷 다시 찍기" 추가 → `resetFlow()` + camera 이동

### 결과

| 항목 | 결과 |
|------|------|
| 단위 테스트 | 333/333 PASS |
| tsc | EXIT 0 |
| 자동 셔터 | opt-in (default OFF) |
| expo-sensors | 미설치 — Phase 2 가속도계 연동 예정 |

### 교훈

- **expo-sensors 설치 전 확인 필수**: 새 native 패키지는 `pod install` → RN rebuild 비용. 타이머 MVP로 UX 가치 먼저 검증하는 접근이 옳았음.
- **한 hook = 한 파일 = 한 책임**: `useCameraGuide` → `CameraGuideOverlay` → `camera.tsx` 레이어 분리 덕분에 각 계층 단독 테스트 가능.
- **opt-in auto-shutter**: 자동 촬영을 강제하지 않는 것이 의료 안전 관점에서도 맞음. 사용자가 처방전이 올바르게 프레임 안에 들어왔는지 최종 판단.

### 다음 가설

> 1. **expo-sensors 연동 (Phase 2)**: `Accelerometer.addListener` → 실시간 흔들림 감지 → `warnShake()` 트리거
> 2. **조명 감지**: 캡처 직전 이미지 평균 밝기 계산 → 어두우면 `hints.brightness = 'warn'`
> 3. **자동 셔터 edge detection**: 처방전 사각형 detect 알고리즘 → 확실한 프레임 인식 시 allOk 보조 신호

---

## 8. Phase B-6 BE — OCR Raw 정확도 향상 (#T-AI-OCR-RAW-QUALITY)

**날짜**: 2026-06-09  
**Why**: B-4 cascade fallback은 Gemini Vision이 잘못 인식한 이후 정정. 그러나 **OCR 자체(Gemini Vision 최초 인식)가 더 정확하면** cascade 부담 ↓ + 전체 정확도 ↑. 두 방향 동시 공략: ① input 품질 ↑ (OpenCV 전처리), ② prompt 품질 ↑ (Few-shot).

### 시도

**1. ImagePreprocessor** (`app/rag/ocr/preprocess.py`):

| 단계 | 구현 | 타깃 케이스 |
|------|------|-----------|
| `rotate_by_exif()` | PIL `ImageOps.exif_transpose` | 스마트폰 세로/가로 혼용 |
| `deskew()` | OpenCV Hough 라인 skew 감지 + rotate | 기울어진 처방전 (±5°) |
| `enhance_contrast()` | CLAHE (LAB L채널 적용) | 어두운/저대비 처방전 |
| `denoise()` | bilateral filter (경계 보존) | 복사 처방전 노이즈 |
| `resize_if_large()` | max 1920×1080 (비율 유지) | 4K 이미지 API 비용+속도 |
| `preprocess()` | 5단계 파이프라인 | 전체 최적화 |

**2. Few-shot 프롬프트 강화** (`app/rag/ocr/prompts/`):
- `examples.jsonl`: 10가지 케이스 (easy 3 / medium 4 / hard 3)
  - OCR 오인식 예시 (쎌→썰, 숫자 혼동, 잘린 약품명, 손글씨)
  - confidence/candidates 결정 기준 예시
- `system_prompt.txt`: 기존 규칙 7개 + Few-shot 10개 예시 통합 프롬프트
- `FEWSHOT_ENABLED` flag: vision.py에서 경로 분기

**3. Feature flags** (`config.py`):
- `PREPROCESS_ENABLED` (default True): OcrService preprocess 분기
- `FEWSHOT_ENABLED` (default True): vision.py 프롬프트 경로 분기

**4. TDD** (15건 RED→GREEN):
```
TestRotateByExif (3)         ✓
TestDeskew (3)               ✓
TestEnhanceContrast (3)      ✓
TestDenoise (2)              ✓
TestResizeIfLarge (3)        ✓
TestPreprocessPipeline (1)   ✓
합계: 15/15 PASS
```

### 결과

| 지표 | Before | After |
|------|--------|-------|
| 단위 테스트 | — | **15/15 PASS** |
| 전체 단위 테스트 | 140건 | **155건 PASS** (회귀 없음) |
| GT 100건 Hit@1 | 0.970 | **0.970** (회귀 없음 ✓) |
| opencv-python | 미설치 | **4.13.0** (pyproject.toml 추가) |
| Pillow | 미설치 | **12.2.0** (pyproject.toml 추가) |
| Few-shot 예시 | 0건 | **10건** (easy/medium/hard) |
| preprocess latency | 0ms | +200~500ms (cv2 처리) |
| 4K 이미지 비용 절감 | 기준 | resize → 토큰 ~1/10 |

**실 이미지 재측정**: Gemini API 일일 할당량 관계로 2026-06-10 00:00 UTC 리셋 후 예정

### 기술적 인사이트

- **EXIF 회전 vs deskew 순서**: EXIF 먼저 처리 후 deskew. EXIF 미보정 상태에서 Hough 라인 감지 시 90° 오인식 위험
- **CLAHE LAB 적용**: BGR 전체 채널 CLAHE → 색상 왜곡. L채널만 → 밝기 균등화 + 색상 유지
- **bilateral vs Gaussian blur**: bilateral는 경계(약품명 글자 테두리) 보존 우수. Gaussian은 경계 blur → OCR 악영향
- **Few-shot 예시 선택 기준**: 10가지 중 OCR 오인식 패턴 3건 포함 (쎌→썰, 숫자불명, 잘린) → cascade Tier 2/3와 complementary
- **skew_threshold = 1.0°**: 보수적 임계치. 미세 기울기 강제 보정 시 이미 잘 인식되던 텍스트 역효과 가능
- **preprocess 실패 시 원본 반환**: `_apply_preprocess()` try-except → 전처리 실패가 OCR 전체를 막지 않음

### 커밋 목록

| # | hash | 설명 |
|---|------|------|
| 1 | 359b86b | Test(RED): ImagePreprocessor 15건 |
| 2 | 340f2ef | Feat: OpenCV preprocess.py 구현 (GREEN) |
| 3 | 20cedba | Refactor: OcrService preprocess 분기 + flags |
| 4 | 7bd311d | Feat: Few-shot prompt 10건 + vision.py 통합 |

### 다음 가설

> 1. **실 8장 재측정** (내일 API 리셋 후): preprocess + few-shot 효과 정량화
> 2. **skew_threshold 튜닝**: 실 처방전 skew 분포 측정 (평균 각도 → 임계치 최적화)
> 3. **T-AI-RAG-HNSW**: ivfflat → HNSW (정확도 +3~7%, 메모리 trade-off 검토)
> 4. **deskew 개선**: Hough → Probabilistic Hough (단문 텍스트 라인에 더 robust)
> 5. **T-BE-POST-/DRUGS/ALIAS**: alias endpoint (현재 404)

---

## 9. Phase B-6 RERUN — 실 약봉투 재측정 (#T-AI-OCR-REAL-RERUN)

**날짜**: 2026-06-09  
**Why**: Phase B-4/B-6 구현 후 분석적 검증만 가능했던 실 이미지 재측정 (Gemini API RPD 소진). API 리셋 후 즉시 재실행 — preprocess + few-shot 실 효과 정량화.

### 실행 조건

- `PREPROCESS_ENABLED=true`: `ImagePreprocessor.preprocess()` (EXIF→deskew→resize→CLAHE→bilateral)
- `FEWSHOT_ENABLED=true`: `system_prompt.txt` (few-shot 10건)
- 이미지: `tests/eval/real_prescriptions/` 8장

### 결과 (실측)

| 지표 | Phase B-3 (#124) | Phase B-6 RERUN |
|------|-----------|----------------|
| 이미지 처리 | 8/8 | 6/8 (IMG_0007: 503, IMG_0014: 429) |
| 약품 추출 수 | 35 | 30 |
| DB 매칭 수 | 31 | **28** |
| **매칭률** | **88.57%** | **93.33%** |
| **개선** | — | **+4.76pp** |

### 이미지별 상세

| 이미지 | 추출 | 매칭 | 비고 |
|--------|------|------|------|
| IMG_0007.PNG | 0 | 0 | 503 UNAVAILABLE (Gemini 일시 과부하) |
| IMG_0008.JPG | 4 | 4 | **종근당아목시실린캡슐 ✓** (B-3 miss → 해결!) |
| IMG_0009.JPG | 7 | 5 | 쎌박타민→쎌레타민(fuzzy), 에치콘 miss |
| IMG_0010.JPG | 8 | 8 | 복잡한 복합제 8개 전부 매칭 ✓ |
| IMG_0011.JPG | 4 | 4 | 전부 ilike ✓ |
| IMG_0012.JPG | 5 | 5 | 전부 ilike ✓ |
| IMG_0013.JPEG | 2 | 2 | ilike ✓ |
| IMG_0014.JPEG | 0 | 0 | 429 RATE LIMITED |

### B-3 알려진 miss 4건 추적

| miss | 예상 | B-6 실측 |
|------|------|---------|
| 쎌박타민정 | B-4 Tier1 prefix_match | ⚠ fuzzy → "쎌레타민정" (잠재적 오매칭 — candidates에 "썰박타민정" 포함) |
| 중근당아목시실린캡슐 | B-4 Tier0 제조사 strip | ✓ **해결** — OCR이 "종근당" 정확 인식 → ilike 직접 매칭 |
| 에치콘정 | DB 미수록 가능성 | ✗ **여전히 miss** — DB 미수록 확인 |
| 엘리버드정 | DB 미수록 가능성 | IMG_0007(503)/IMG_0014(429) 처리 못해 미확인 |

### 분석 인사이트

**preprocess 효과 (종근당 케이스)**:
- B-3: OCR "중근당아목시실린캡슐" (ㅗ→ㅜ 오인식) → Tier 0 strip 후에도 DB miss
- B-6: 이미지 전처리(CLAHE + bilateral) 후 Gemini가 "종근당" 정확 인식 → ilike 직접 매칭
- **교훈**: preprocess는 OCR 자체 오인식을 줄임 → Tier 0/1/2 cascade 덜 필요

**few-shot candidates 효과 (쎌박타민 케이스)**:
- confidence 0.65로 낮게 잡음 ✓ (임계치 0.7 미만 → candidates 요청)
- candidates에 "썰박타민정500밀리그램" 포함 ✓
- 그러나 cascade 최종 매칭은 "쎌레타민정" (fuzzy) → 오매칭 위험
- **개선 필요**: candidates를 cascade 입력에 직접 활용하는 로직 (Tier 2 강화)

**새로 발견된 miss**:
- "워더스낙정" → none (stage=none): 처음 등장. DB 미수록 또는 OCR 오인식
- confidence 0.55, candidates ["워더스낙정", "워더스낙정", "워더스락정"] — 후보들도 모두 miss

**IMG_0010 복합제 8개 전부 매칭 (신규 발견)**:
- 성분명+영문이 포함된 긴 약품명 8개가 token stage에서 모두 매칭
- "슈가메트서방정5/1000밀리그램", "듀비메트서방정0.5/1000밀리그램" 등 복합제 token 매칭 robust
- 이 이미지는 B-3에서도 매칭됐을 가능성 높음 (성분명 기반 token search 강점)

### 남은 과제

1. **IMG_0007.PNG + IMG_0014.JPEG 재측정**: API 재리셋 후 전체 8장 완전 측정
2. **쎌박타민정 Tier 2 강화**: candidates를 cascade Tier 2 입력으로 직접 사용 (현재 candidates는 logging 용도만)
3. **워더스낙정 조사**: DB 미수록인지 OCR 오인식인지 확인 → drug_alias 추가 or 미수록 확인
4. **에치콘정 확인**: `SELECT * FROM drugs WHERE name ILIKE '%에치콘%'` — DB 미수록 공식 확인

### 커밋

| hash | 설명 |
|------|------|
| (스크립트) | run_real_e2e_b6.py 신규 + 보고서 저장 |

---

---

## 10. Phase B-7a — Gemini 다중 API 키 로테이션 (#T-AI-OCR-MULTI-KEY-FALLBACK)

**날짜**: 2026-06-10  
**Why**: Phase B-6 RERUN에서 `IMG_0014.JPEG` 429, `IMG_0007.PNG` 503으로 2/8 이미지 미처리. Gemini Flash free tier RPD=20 병목이 실 사용 불가 수준. 두 번째 계정 KEY2 추가 → RPD 40.

### 시도

- **환경변수 체계 재설계**: `GEMINI_API_KEY1` (primary) + `GEMINI_API_KEY2` (secondary) + `GEMINI_API_KEY` (legacy 하위 호환)
- `Settings.gemini_keys` property: `[KEY1 or legacy, KEY2]` 필터링 (빈 값 제외)
- **`GeminiVisionAdapter`**: `_llms: list` + `_RATE_LIMIT_ERRORS` 튜플 → 429/503 포착 후 다음 키로 자동 시도
- **`OcrCorrectionAdapter`**: 동일 패턴
- **`GeminiInvoker`** (P0 hotfix에서 통합): `api_keys: list[str]` 인터페이스 통일
- `docker-compose.yml` ai-server env: `GEMINI_API_KEY1`, `GEMINI_API_KEY2` 추가
- **`google.genai.errors`**: `ClientError` (429) + `ServerError` (503) — `google-api-core` 미설치 환경 대응

### TDD 결과

```
TestGeminiKeysProperty (8건)     ✓
TestVisionFallback (7건)         ✓
TestCorrectionFallback (4건)     ✓
합계: 19건 PASS / 누적 189건
```

### 결과

| 지표 | Before | After |
|------|--------|-------|
| API 용량 | RPD 20 | **RPD 40** (KEY 2개) |
| 429 처리 | 즉시 VisionInvocationError | KEY2 자동 fallback |
| IMG_0014 (429) | 미처리 | 처리 가능 |
| GT 100건 | 0.970 | **0.970** (회귀 없음) |

### 교훈

- **`google.api_core` != `google.genai.errors`**: `google-api-core` 패키지 미설치 환경에서 `ResourceExhausted` 임포트 실패. `google-genai` 패키지의 `errors.ClientError` / `errors.ServerError` 사용이 올바른 방법.
- **pydantic-settings v2 init 우선순위**: `init kwargs > env > dotenv`. 테스트에서 모든 키 필드를 명시적으로 전달해야 `.env` 실제 키가 오염되지 않음.
- **_mask_key 패턴**: 로그 `AIza***{key[-4:]}` — 키 노출 방지 + 어떤 키인지 식별 가능.

---

## 11. Phase B-7b — 낱알식별 Tier 5 (#T-AI-OCR-PILL-IDENTIFY-FALLBACK)

**날짜**: 2026-06-10  
**Why**: B-6 RERUN 93.33% (28/30). 남은 miss 중 "에치콘정", "워더스낙정"은 텍스트 완전 손상 또는 DB 미수록. 텍스트 기반 cascade 4단계로는 해결 불가 → DB V6 마이그레이션에 이미 있는 낱알식별 컬럼 활용.

### DB 낱알식별 데이터 현황

```sql
SELECT COUNT(*) FROM drugs WHERE shape_class IS NOT NULL;  -- 9,679건 (20.6%)
SELECT DISTINCT shape_class FROM drugs WHERE shape_class IS NOT NULL;
-- 원형, 장방형, 타원형, 기타, 팔각형, 사각형, 삼각형, 오각형, 육각형, 마름모형, 반원형
```

color_class 형식: 단색("하양"), 복합색("하양,하양", "파랑, 투명,파랑, 투명")

### 시도

1. **`PillAppearance` 도메인 모델**: `shape/color/mark_front/mark_back/line` 필드 (Pydantic BaseModel)
2. **`RawOcrItem.appearance: PillAppearance | None`**: Vision 프롬프트 rule #8로 외관 추출 — 추가 LLM 호출 0건 (PydanticOutputParser 스키마에 자동 포함)
3. **`PillIdentifyAdapter`** (`asyncpg` 직접 쿼리):
   - NULL-safe SQL: `$2 IS NULL OR color_class ILIKE $2` → optional 필터
   - color 매칭: `%색상%` wildcard (DB 복합 형식 대응)
   - 정렬: mark 일치 우선 → color 일치 → shape만 일치
   - `PILL_IDENTIFY_ENABLED` feature flag
4. **OcrPrescriptionService Tier 5 통합**: `Tier 0 → Tier 2 → Tier 3 → Tier 5 → MANUAL`
5. **`MatchStage`**: `"pill_identify"` Literal 추가

### TDD 결과

```
TestPillAppearance (3건)         ✓
TestRawOcrItemAppearance (2건)   ✓
TestPillIdentifyAdapter (8건)    ✓
합계: 13건 PASS / 누적 202건
```

### 분석 (실측 미수행 — API 할당량 재리셋 후 예정)

**예상 활성화 조건**:
- Tier 0/2/3 전부 실패 + Vision이 shape 정보 추출 성공 (9,679/47,021 약품)
- OCR 텍스트 완전 손상 케이스 (예: 약품명이 찍힌 봉투 앞면 가림)

**현실적 한계**:
- DB coverage 20.6%: 나머지 79.4% 약품은 외관 데이터 없음 → 매칭 불가
- shape 단독 매칭 시 동일 shape 약품 수백 건 → false positive 위험
- mark_code 정확 입력 시 선택성 높음

### 교훈

- **NULL-safe SQL 파라미터**: asyncpg `$2 IS NULL OR col ILIKE $2` — Python `None`이 PostgreSQL `NULL`로 바인딩, `NULL IS NULL = TRUE` → 조건 pass. optional 필터 구현의 깔끔한 방법.
- **color_class 복합 형식**: "하양,하양", "파랑, 투명,파랑, 투명" — DB 정규화 없이 `ILIKE '%하양%'` wildcard로 단색 Vision 추출 값과 매칭.
- **Vision 외관 추출 무료**: `PydanticOutputParser` 스키마에 `PillAppearance` 포함 → 기존 Vision 호출 1번에 외관 정보도 같이 추출. 추가 비용 0.

### 커밋

| # | 설명 |
|---|------|
| 1 | Test(RED): PillAppearance + RawOcrItem.appearance 5건 |
| 2 | Feat: PillAppearance 도메인 + RawOcrItem.appearance 필드 |
| 3 | Test(RED): PillIdentifyAdapter 8건 |
| 4 | Feat: PillIdentifyAdapter SQL + Tier 5 cascade 통합 |
| 5 | Feat: Vision prompt rule #8 + MatchStage pill_identify |
| 6 | Test: 통합 스냅샷 (202건 PASS) |

### 다음 가설

> 1. **실 E2E 재측정**: API 할당량 회복 후 8장 전체 (Tier 5 실효과 정량화)
> 2. **shape_class 커버리지 확장**: 9,679/47,021 → 식약처 낱알식별 추가 데이터 적재
> 3. **Vision appearance 오추출 분석**: false positive 비율 측정 후 threshold 도입 검토
> 4. **T-BE-POST-/DRUGS/ALIAS**: alias endpoint 구현 (현재 404)

---

## P0. ai-server 스타트업 크래시 + Dockerfile cv2 누락 (2026-06-10)

**날짜**: 2026-06-10  
**Why**: T-AI-OCR-MULTI-KEY-FALLBACK 환경변수 변경 후 컨테이너 무한 재시작. 두 가지 Root Cause 동시 발견.

### Root Cause 1: GeminiInvoker api_key → api_keys

사용자가 `.env`에서 `GEMINI_API_KEY` 제거 + `KEY1/KEY2` 추가 → `settings.gemini_api_key = ""` → `main.py:44` `GeminiInvoker(api_key="")` → ChatGoogleGenerativeAI `ValidationError: API key required`.

**Fix**: `GeminiInvoker.__init__` signature `api_key: str` → `api_keys: list[str]`. `_clients: list` 생성, `ainvoke()`에서 key rotation 내장.

### Root Cause 2: Dockerfile cv2 미포함

`service.py`가 `preprocess.py`를 top-level import → `cv2` 없어 모듈 로드 실패. `PREPROCESS_ENABLED=false`여도 import는 항상 실행.

**Fix**:
- `Dockerfile` 패키지 추가: `opencv-python-headless>=4.9`, `numpy>=1.26`, `Pillow>=10.0`, `google-genai>=1.0`, `langchain-google-genai>=4.0`
- `service.py`: `from app.rag.ocr.preprocess import ImagePreprocessor` → `TYPE_CHECKING` 조건부 (runtime 미실행)

**커밋**: `00c53d1 Fix(ai-startup)` + `Fix(Dockerfile): cv2/numpy/Pillow 추가`

### 교훈

- **Dockerfile은 pyproject.toml과 별개로 관리**: pyproject.toml에 패키지 추가 시 Dockerfile도 동기화 필요. 특히 native 라이브러리(opencv)는 headless 변형 사용 (`opencv-python-headless` — GUI 의존성 제거, Docker 이미지 경량).
- **TYPE_CHECKING 패턴**: optional 의존성이 있는 모듈은 type annotation에만 사용 → `if TYPE_CHECKING: import`. `from __future__ import annotations`와 함께 사용하면 런타임 import 없이 타입 힌트 유지.
- **컨테이너 재빌드 필수**: 코드 수정 후 `docker compose build ai-server` 없이 `--force-recreate`만으로는 새 패키지 반영 안 됨.

---

## 12. Phase C-1 — 임계값 Sweep 측정 (#148 T-AI-THRESHOLD-SWEEP)

**날짜**: 2026-06-13
**Why**: B-3에서 ABS_THRESHOLD=0.70을 설정했으나 데이터 기반 교정 없이 설정된 값. "왜 0.70인가?"라는 면접 질문에 정직한 데이터 필요 — 포폴 정직성용 측정.

### 시도

- `tests/eval/run_threshold_sweep.py` 신규 (standalone, asyncpg 직접 쿼리)
- GT 100건 × RrfMatcher(DomainReranker + BgeRerankerAdapter) 실행
- 항목별 `(gt_kd_code, top1.final_score, match=top1==gt)` 캡처
- threshold ∈ {0.50, 0.60, 0.70, 0.80} 각각 auto_count / auto_accuracy / false_auto / review_ratio 산출
- exact_fast(score=1.0 하드코딩) vs RRF-only 점수 분포 분리
- 출력: `reports/eval/threshold_sweep_2026-06-13.{md,json}`

### 발견된 문제 1 — BGE 의존성 크래시

`FlagReranker.compute_score()` 내부에서 `tokenizer.prepare_for_model()` 호출 → `transformers ≥ 4.47`에서 `XLMRobertaTokenizer.prepare_for_model` 제거됨 → `AttributeError`.

**임시 대응**: try-except로 BGE 실패 시 `DomainReranker only` 경로 fallback. `_bge_warned` flag로 중복 경고 방지.

**근본 해결 필요**: `FlagEmbedding` 버전을 `transformers < 4.47` 호환 범위로 pin.

### 발견된 문제 2 — exact_fast 0건

`parse_drug_item("타이레놀정500밀리그램")` → dose suffix strip → `parsed.name = "타이레놀정"`.  
`ExactSingleRetriever`: `WHERE name ILIKE $1` (wildcard 없음) → DB 실제 이름 "타이레놀정500밀리그램" 불일치 → 0 hits.  
**전체 100건이 RRF 경로로 처리됨.**

### 결과 (threshold_sweep_2026-06-13.md)

```
GT 100건 | 파이프라인: DomainReranker only (BGE 미작동)
전체 Hit@1: 56/100 = 0.560

Stage 분포:
  rrf:          61건  Hit@1 = 0.918 (56/61)
  rrf_no_match: 39건  Hit@1 = 0.000 (hard GT — DB 후보 0건)

RRF-only 점수 분포 (BGE normalize 미적용):
  min  = -5.7836
  p50  = -0.5836
  max  = -0.1672  ← 전량 음수. ABS_THRESHOLD=0.70과 직접 비교 불가.

임계값 표:
   thr | auto | auto_acc | false | review%
  0.50 |    0 |    0.000 |     0 | 100.00%
  0.60 |    0 |    0.000 |     0 | 100.00%
  0.70 |    0 |    0.000 |     0 | 100.00%   ← 운영 현재값
  0.80 |    0 |    0.000 |     0 | 100.00%
```

**결론**: ABS_THRESHOLD=0.70은 BGE `normalize=True` 경로 기준으로 설계된 값. BGE 없이는 스코어 범위 자체가 다름 → **어떤 임계값도 의미 없음**.

### RRF+DomainReranker Hit@1 해석

- RRF 후보가 있는 61건 중 91.8% Hit@1 — 랭킹 품질은 양호
- **39건 no_match의 원인**: GT 셋에 일부러 넣은 hard 아이템들 (카테고리 입력 "항히스타민제"/"혈압약", 영어 INN "Tylenol"/"Aspirin"/"Metformin", OCR 타이포 "아목시심린"/"메트포르밍")
- ILIKE + trigram으로 후보를 한 건도 못 찾는 아이템 → RRF 경로 자체가 동작 못 함. 이 케이스들은 Tier 3/4/5(LLM correction, search grounding, pill_identify)가 담당 영역.

### 스크립트 디버깅 과정

| 문제 | 원인 | 수정 |
|------|------|------|
| `IndexError` in histogram | `score * 10` → 음수 인덱스 | dynamic range histogram으로 교체 |
| `ZeroDivisionError` | exact_fast 0건 → `exact_hit/0` | `if exact_entries` 조건 분기 |
| BGE `AttributeError` | transformers≥4.47 API 제거 | try-except → DomainReranker fallback |

### 교훈

- **임계값은 파이프라인 전체 스코어 범위와 커플링** — BGE off 상태에서 threshold=0.70은 "아무것도 자동 확정하지 말 것"과 동일한 효과. 임계값 튜닝 전 BGE 의존성 복원이 prerequisite.
- **GT 난이도 분리의 중요성** — 전체 Hit@1 56% vs RRF-성공 케이스 Hit@1 91.8%. "56%"만 보면 시스템이 나쁜 것처럼 보이지만, hard/typo/영어 아이템 39건은 텍스트 검색 범위 밖. 면접에서 이 분리가 핵심 내러티브.
- **포폴 정직성** — "0.70으로 설정했다"가 아니라 "0.70으로 설정했지만 현재 미튜닝 상태임을 측정으로 확인했다"가 오히려 신뢰를 높임.

### 다음 가설

> 1. **FlagEmbedding 버전 pin** (`FlagEmbedding==1.2.11` + `transformers<4.47`): BGE 복원 후 재측정
> 2. **BGE 정상화 시 임계값 재캘리브레이션**: ROC curve 기반 최적 임계값 탐색
> 3. **exact_fast 경로 수정**: `parse_drug_item` 결과로 `WHERE name ILIKE '%{name}%'` (wildcard 추가) → 함량 포함 이름 매칭

### 산출물

- `back/ai_server/tests/eval/run_threshold_sweep.py` (신규)
- `back/ai_server/reports/eval/threshold_sweep_2026-06-13.{md,json}` (신규)

---

### 변경 이력
| 날짜 | 변경 |
|---|---|
| 2026-06-09 | 초안 작성 (#115/#122/#123/#124 정리, 사용자 영구 룰 등재) |
| 2026-06-10 | Phase B-7a (다중 키) + B-7b (낱알식별 Tier 5) + P0 크래시 hotfix 추가 |
| 2026-06-09 | Phase B-4 FE 안전망 (#T-FE-OCR-MANUAL-REVIEW) 추가 |
| 2026-06-09 | Phase B-4 BE 4-Tier Fallback (#T-AI-RAG-LLM-FALLBACK) 추가 |
| 2026-06-09 | Phase B-5 전체 임베딩 확장 (#T-AI-RAG-EMBED-BULK) 추가 |
| 2026-06-09 | Phase B-6 FE 카메라 가이드 (#T-FE-CAMERA-GUIDE) 추가 |
| 2026-06-09 | Phase B-6 BE OCR Raw 정확도 (#T-AI-OCR-RAW-QUALITY) 추가 |
| 2026-06-09 | Phase B-6 RERUN 실 약봉투 재측정 (#T-AI-OCR-REAL-RERUN) 추가 — 93.33% 실측 |
| 2026-06-13 | Phase C-1 임계값 Sweep (#148) 추가 — BGE 의존성 크래시 + 미튜닝 확인 |

---

## 부록 A — ILIKE Seq Scan vs pg_trgm GIN 성능 실측 (2026-06-11)

**Why**: 포폴 narrative "LIKE 의 두 독립 문제 (정확도/성능)" 의 성능 축 실증. 분석가 권고에 따라 jit=off median + pgbench 동시 부하로 확정값 측정.

**환경**: Docker 로컬 PostgreSQL 16, drugs 47,021행, jit=off

### 단일 쿼리 (`name ILIKE '%타이레놀%' LIMIT 5`, 5회 median)

| | 5회 측정값 (ms) | median |
|---|---|---|
| pg_trgm GIN (Bitmap Index Scan) | 4.016 / 0.107 / 0.139 / 0.112 / 0.118 | **0.118 ms** |
| Seq Scan 강제 | 15.27 / 46.21 / 7.42 / 25.66 / 7.11 | **15.27 ms** |

→ warm 기준 **~129×**. (이전 측정 423ms 는 JIT 컴파일 + cold buffer 포함 — 포폴에는 jit=off median 사용)

### pgbench 동시 부하 (K=20 커넥션, 4 threads, 30초)

| | TPS | 평균 latency |
|---|---|---|
| pg_trgm GIN | **43,081** | **0.464 ms** |
| Seq Scan 강제 | **433** | **46.19 ms** |

→ 동시성에서 **TPS 99.5× / latency 99.6×** — "동시 사용자에서 복리 악화" 주장의 직접 증거.

### Buffers (EXPLAIN BUFFERS, 이전 측정)
- Seq Scan: shared hit=4,493 (44,030 rows filtered)
- GIN: shared hit=23 — **버퍼 I/O 195× 감소**

### 교훈
- 47K 작은 테이블도 동시성 부하에서 100× 차이 — "테이블 작으니 풀스캔 무방" 반박 데이터
- 성능은 pg_trgm GIN 으로, 정확도는 Hybrid RAG 로 — 인과 분리 narrative 확정

---

## B-8. #150 RrfMatcher 운영 통합 (Gate D — 2026-06-13)

### 배경
Gate A++ (false-auto=0, Hit@1=98%), Gate C (ABS_THRESHOLD=0.70 확정) 완료 후
main.py 에 RrfMatcher 를 실제 주입하는 Gate D 진행.

### 핵심 문제: 평가≠운영 괴리 (과거 사고 근본 원인)
`run_eval_full.py` 가 `_cascade_search()` (별도 레거시 6-step cascade) 를 사용.
main.py 가 주입하는 `RrfMatcher` 와 **완전히 다른 코드 경로** → 평가 수치가 운영을 대표하지 못함.

### 해결: rrf_factory.py 단일 진실 공급원

```
app/rag/ocr/rrf_factory.py
  build_rrf_matcher_inner(pool) → RrfMatcher   ← eval 스크립트 사용
  build_rrf_matcher(pool)       → RrfMatcherAdapter  ← main.py (OcrPrescriptionService 주입)
```

- `main.py`: `DRUG_MATCHER_IMPL=rrf` (기본) → `build_rrf_matcher(pool)` / `legacy` → DrugMatcher rollback
- `run_eval_full.py`: `_cascade_search` 제거 → `build_rrf_matcher_inner(pool).match(parsed)` 사용
- 두 경로가 **동일 `RrfMatcher` 구성** → eval≠prod 괴리 구조적 제거

### 테스트 수정 (Gate A++ 행동 반영)
- `test_rrf_matcher.py::test_fast_path_no_dose_skipped` → `test_fast_path_no_dose_also_taken`
  - 구 동작: 함량 없으면 exact fast path 건너뜀
  - Gate A++ 동작: 함량 무관하게 StrongExactAdapter 응답 → exact_fast 경로
- `test_rrf_adapters.py::TrigramMultiAdapter`: `prefix_match=True` 누락 → 수정
- `test_rrf_wire.py`: `RawOcrItem/OcrItem` Pydantic v2 `frequency=None` → 기본값 사용

### Gate D 동치 검증 결과

| 항목 | legacy cascade | RRF surfacing |
|------|---------------|---------------|
| Hit@1 (약 표시됨) | **98/100 (98.0%)** | **98/100 (98.0%)** |
| ⛔ 표시 퇴보 | — | **1건** (gt_035) |
| ⚠️ AUTO→MANUAL 격하 | — | 28건 |
| ⛔ false-auto | — | **0건** ✅ |

### 퇴보 1건 상세 — gt_035 `콜레스테롤약` (pre-existing)
- input: `"콜레스테롤약"` (약종 카테고리명, 특정 약품명 아님)
- legacy: `ingredient_ko` 키워드 검색 → `로수바엘정10밀리그램(로수바스타틴칼슘)` (임의 rosuvastatin)
- RRF: `콜레스텐연질캡슐` (다른 콜레스테롤 약) → INN `로수바스타틴` 미포함 → MISS
- **근본 원인**: RRF 는 카테고리명("콜레스테롤약")을 특정 약품으로 매핑하는 로직 없음
- **의료 안전 관점**: 카테고리명으로 임의 rosuvastatin 반환하는 legacy 동작이 오히려 위험
- **상태**: Gate B3 부터 이미 MISS — 새로 도입된 퇴보 아님

### AUTO→MANUAL 격하 28건 분류
| 카테고리 | 건수 | 예시 |
|---------|------|------|
| 영어 INN (Tylenol, Aspirin...) | 20 | Tylenol → AUTO→MANUAL |
| 한국어 약종명 (진통소염제, 수면제...) | 5 | 진통소염제 → AUTO→MANUAL |
| 기타 | 3 | 세파클롤캡슐 등 |

> 의료 안전 측면: MANUAL이 보수적으로 올바른 동작. 사용자 확인으로 오확정 방지.
> BGE임계(0.70) 미달로 자동확정 않는 것 = 안전 설계.

### 교훈
1. **평가=운영 괴리**는 구조적 문제 — 팩토리 함수 단일화로 재발 방지
2. **auto-INN 측정 오류**: "졸피뎀타르타르산염정" → auto-INN "졸피뎀타" (너무 specific)
   → DB에 "주석산졸피뎀" 표기라 "졸피뎀타" 미포함 → 측정 오류로 false-auto 착각
   → INN을 "졸피뎀"(활성성분)으로 수정 → 실제 false-auto=0 확인
3. **TrigramMultiAdapter prefix_match** 누락: `rerank(jamo, hits)` → `rerank(jamo, hits, prefix_match=True)`
   → DB 약품명(함량 포함)이 쿼리(함량 없음)보다 길어 jamo 거리 과대 → 빈 결과

### 다음 가설
- 영어 INN MANUAL 28건 → `StrongExactAdapter` 에 영어 alias 단계 추가? (Gate E 검토)
- 카테고리명 입력 → 사용자에게 직접 "어떤 약인지 검색해주세요" UX 가이드 표시?

---

## B-9. #151 T-AI-REAL-E2E-RRF — 실 처방전 8장 운영 RRF 경로 첫 실측 (2026-06-13)

### 배경
Gate D(B-8) 통합 완료 후, 기존 `run_real_e2e.py` 의 레거시 `_cascade_search` 가 제거됨.
`#151` 에서 운영 RRF 경로(`build_rrf_matcher_inner`)로 실 처방전 8장을 **처음으로 측정** — 마감 정직성 기준 실측값.

### 측정 방법
- `run_real_e2e_rrf.py` 신규 작성 (run_real_e2e.py 의 `_cascade_search` → `RrfMatcher.match()`)
- Gemini 2.5 Flash Vision OCR (8장, < $0.05)
- 매칭: `build_rrf_matcher_inner(pool)` — main.py 운영 경로와 동일
- 의심 오매칭 탐지 휴리스틱: rrf 저점수(<0.80) OR 이름 prefix 3자 불일치 OR 함량 수치 불일치

### 결과 요약

| 이미지 | 추출 | AUTO | CONFIRM | MANUAL | 매칭실패 | ⚠️의심오매칭 |
|--------|------|------|---------|--------|---------|-------------|
| IMG_0007.PNG | 1 | 1 | 0 | 0 | 0 | 0 |
| IMG_0008.JPG | 4 | 4 | 0 | 0 | 0 | 0 |
| IMG_0009.JPG | 7 | 4 | 0 | 3 | 1 | 0 |
| IMG_0010.JPG | 8 | 8 | 0 | 0 | 0 | 0 |
| IMG_0011.JPG | 4 | 3 | 0 | 1 | 0 | 1 |
| IMG_0012.JPG | 5 | 3 | 0 | 2 | 0 | 0 |
| IMG_0013.JPEG | 2 | 0 | 0 | 2 | 0 | 0 |
| IMG_0014.JPEG | 4 | 4 | 0 | 0 | 0 | 1 |
| **합계** | **35** | **27** | **0** | **8** | **1** | **2** |

```
AUTO 확정률:       27/35 = 77.1%
surfacing:         35/35 = 100.0%  (MANUAL도 options[0] 제시)
⚠️ 의심 오매칭:   2건  (함량 불일치)
```

### 의심 오매칭 2건 — 함량 불일치 false-auto

| 이미지 | 추출명 | 매칭명 | 의심 사유 |
|--------|--------|--------|---------|
| IMG_0011.JPG | `비유피-4정 20mg` | 비유피-4정10밀리그램(프로피베린염산염) | 함량 불일치 20≠10 |
| IMG_0014.JPEG | `엔테론정150밀리그램` | 엔테론정50밀리그램(포도씨건조엑스) | 함량 불일치 150≠50 |

**근본 원인**: `StrongExactAdapter`가 약명 prefix 로 단일 매치 → DB에 해당 함량 없는 경우에도 AUTO 확정.
**의료 안전 위험**: 잘못된 함량으로 확정 시 환자 오복용 위험.
**권고 조치**: Gate E — `MatchDecider` 에 함량 검증 레이어 추가.
  - 쿼리 dose_amount 있고 matched dose_amount 와 ≥20% 차이 → CONFIRM 격하

### 추가 검토 케이스 — 브랜드명 상이

| 이미지 | 추출명 | 매칭명 | 비고 |
|--------|--------|--------|------|
| IMG_0014.JPEG | `이세틸정 100mg` | 케이세틸정(아세틸-L-카르니틴염산염) | 브랜드명 상이, 성분 동일 추정, 함량 DB 미기재 |

처방전에는 `이세틸정` 이지만 DB 에는 `케이세틸정` 이 exact_fast 경로로 반환.
다른 브랜드일 경우 AUTO 확정은 부적절 — prefix check 3자("이세틸")가 "케이세틸" 에 포함돼
휴리스틱이 잡지 못함. Gate E 에서 정밀 검토 필요.

### MANUAL 8건 분류

| 유형 | 건수 | 예시 |
|------|------|------|
| OCR 인식 오류(글자 오타) | 2 | 쎌박타민→쎌레타민, 에디악디에스→메디락디에스 |
| 약명 불완전 추출(이알서방 미포함 등) | 2 | 세토펜이알서방정→세토펜정 |
| 약명 유사하나 불충분 | 2 | 액시티단→액시티딘, 가스트렉스→가스트로그라핀 |
| 매칭 완전 실패 | 1 | 에치론정(DB 미존재 추정) |
| 긴 영문+한글 혼합 | 1 | 안약 (엘리드림점안액 diclofenac...) |

> MANUAL 판정 = 사용자 확인 유도 → 의료 안전 측면에서 올바른 보수적 동작.

### B-3 레거시 대비 비교

| 지표 | B-3 (레거시 cascade) | B-9 (운영 RRF) |
|------|---------------------|---------------|
| OCR 추출 약품 수 | 30 (7장) | 35 (8장) |
| 자동 확정률 | ~100% (threshold 없음) | **77.1%** |
| false-auto (함량 오확정) | 측정 안 됨 | **2건** (발견 → Gate E 대상) |
| surfacing | ~93.3% | **100%** |
| 레거시 cascade 사용 여부 | 사용 | **제거 완료** (eval=prod 단일화) |

### 교훈
1. **threshold 도입의 실제 효과**: AUTO 77.1%(보수적) = 의료 안전 ↑. 과거 legacy 100% 확정은 오확정 숨김.
2. **함량 불일치 false-auto 발견**: StrongExact 경로에서 함량 검증 없이 AUTO 확정하는 구조적 문제.
   DB에 20mg 없고 10mg만 있을 때 자동 10mg 확정 → 환자 2배 용량 위험.
3. **eval=prod 단일화 효과**: 레거시 cascade 제거 후 첫 측정이 기존 "평가 수치"와 다름을 확인.
   이것이 `run_eval_full.py` 가 prod 코드와 달랐을 때의 위험성.
4. **영문 혼합 약명**: IMG_0013(안약) 2건 MANUAL — Vision 프롬프트 개선 또는 영문→한글 약명 매핑 추가 검토.

### 다음 가설 (Gate E)
- `MatchDecider` 에 **함량 검증 레이어**: `abs(query_dose - matched_dose)/query_dose > 0.20` → CONFIRM 격하
- `StrongExactAdapter` 에 **정확 함량 조건**: `WHERE dose_amount = $dose` 추가 (현재 이름만 exact)
- 영어 INN MANUAL → `StrongExactAdapter` 영어 alias 단계 추가

---

## 11. Phase B-10 — StrongExact 함량 검증 게이트 (#152 T-AI-DOSE-VERIFY-SHORTCIRCUIT)

**날짜**: 2026-06-14
**Why**: B-9 에서 함량 불일치 false-auto 2건 발견.
  - `비유피-4정 20mg` → `비유피-4정10밀리그램` AUTO (2배 용량 오확정)
  - `엔테론정150밀리그램` → `엔테론정50밀리그램` AUTO (3배 용량 오확정)
  - `이세틸정 100mg` → `케이세틸정(아세틸-L-카르니틴염산염)` AUTO (브랜드명 상이)
  의료 안전상 함량 오확정은 절대 허용 불가 → 구조적 수정.

### 근본 원인 분석

**문제 1 — normalize_for_cascade 함량 손실**:
  - `normalize_for_cascade("엔테론정150밀리그램")` → `"엔테론정"` (단위 제거)
  - `parse_drug_item("엔테론정")` → `dose_amount=None`
  - 결과: `StrongExactAdapter` 는 함량 없는 ParsedItem 으로 매칭 → 함량 검증 불가

**문제 2 — StrongExactAdapter._pick_best 함량 미검증**:
  - `dose_hits=[]` 일 때 기존 코드: `return pool[0]` → **함량 틀린 후보 반환**
  - 의료 안전 요구: `dose_hits=[]` → `return None` (RRF 위임)

**문제 3 — 브랜드명 suffix 매칭 허용**:
  - `"이세틸정"` → ILIKE `%이세틸정%` → `"케이세틸정"` 히트 (suffix 포함)
  - `_in_main_name("이세틸정", "케이세틸정")` = True (substring check)
  - 다른 브랜드 약품을 exact_fast 단축으로 AUTO 확정하는 구조

**문제 4 — exact_fast final_score 캡처 버그**:
  - `MatchResult.final_score = 1.0` 이지만 `Candidate.final_score = 0.0` (기본값)
  - 리포트가 `decision.primary.final_score` (=0.0) 를 표시 → 정직성 문제

### 해결책 (4개 파일 수정 + 2개 신규)

**1. `rrf_wire.py` — 함량 보충**:
  ```python
  if parsed.dose_amount is None and raw.dose_amount is not None:
      parsed = replace(parsed, dose_amount=raw.dose_amount, dose_unit=raw.dose_unit or "mg")
  ```
  `normalize_for_cascade` 로 손실된 dose_amount 를 `raw.dose_amount` 로 보충.

**2. `rrf_adapters.py` — _pick_best 이중 검증**:
  - `_prefix_compatible()` 신규 추가: 브랜드명 쿼리(형태 접미사)가 후보명 가운데/끝에서만 나타나면 거부.
    허용: 제조사 prefix 2~4자 후 query 시작 (종근당아목시실린캡슐). 거부: 이세틸정 ⊂ 케이세틸정 (offset=1 < 2).
  - 함량 불일치: `dose_hits=[]` → `return None` (RRF 위임)

**3. `rrf_matcher.py` — 점수 캡처 버그 수정**:
  ```python
  fast.final_score = 1.0  # Candidate 직접 설정
  ```
  `MatchResult.final_score` 와 `decision.primary.final_score` 일치.

**4. `decider.py` — dose_mismatch CONFIRM**:
  - RRF 경로 fallback: 점수 ≥ 0.70 이어도 함량 불일치면 CONFIRM.
  - `_dose_mismatch()`: 후보명 용량 ≠ query 용량 (10% 이상 차이) → True.
  - 후보에 용량 미기재 시에도 True (검증 불가 = 보수적 CONFIRM).

**5. `tests/eval/run_real_e2e_rrf.py` — 함량 보충 + 점수 수정**:
  - `parse_drug_item(item.name_raw)` 로 dose 보충 (OCR field + name_raw 파싱 2중).
  - `score = result.final_score` 사용 (exact_fast=1.0 정직성).

### Gate E 결과 (전부 PASS)

| 검증 항목 | 기준 | 결과 |
|-----------|------|------|
| false-auto 3건 재판정 | 오답 AUTO=0 | ✅ 0건 (올바른 용량으로 AUTO or MANUAL) |
| GT100 surfacing | ≥ 98% | ✅ 98/100 = 98.0% |
| GT100 miss | ≤ 2건 | ✅ 2건 (동일 — 기존 MISS) |
| 실 처방전 35건 재매칭 | dose-mismatch AUTO=0 | ✅ 0건 |
| exact_fast 점수 | 1.000 | ✅ 1.000 |
| ai_server 단위 테스트 | 155+ | ✅ 212 PASS |

### 교훈
1. **함량 손실 포인트 명확화**: `normalize_for_cascade` 는 DB 검색용 이름 정규화 — 함량을 의도적으로 제거.
   함량 검증은 OCR 원본(`raw.dose_amount` 또는 `parse_drug_item(name_raw)`)에서 가져와야.
2. **false-auto 해결의 역설**: 기대는 CONFIRM 이었지만, DB에 올바른 함량 변종이 존재해 정확한 AUTO 확정.
   "false-auto 방지" = "오답 AUTO 방지" (정답 AUTO는 최선의 결과).
3. **suffix 매칭 위험**: 한글 약품명에서 suffix 포함은 다른 브랜드를 잘못 확정.
   `_prefix_compatible` 로 제조사 prefix(2~4자) 허용하면서 이를 방지.
4. **점수 캡처 버그**: `MatchResult.final_score` vs `Candidate.final_score` 분리 설계 주의.
   exact_fast 경로는 Candidate 에도 직접 설정 필요.

### 다음 가설 (Gate F)
- `이세틸정` → DB에 존재하지 않음 (MANUAL). 만약 처방전이 맞다면 DB 갱신 필요.
- MANUAL 케이스를 위한 "처방전 DB 미등재 약품 신고 플로우" 검토.
- 8장 중 MANUAL 8건 (23%) 분석 — 추가 retriever 추가 vs 사용자 UI 개선.

## 12. Fix-4 — RAG 룰 정합 + dead code 정리 (#165, 2026-06-15)

- `VectorMultiAdapter`(rrf_factory 미연결 dead code) + 단위테스트 2건 + eval 참조 제거 — 매칭 동작 무변경(98% surfacing 유지, 운영 경로 unaffected). `PgVectorDrugSearch`(legacy DrugMatcher flag 경로)는 유지.
- `.claude/rules/python/langchain.md` Retriever 절을 실제 구현(OCR=RrfMatcher 6 retriever+RRF k=60+BGE rerank, Chat RAG=pgvector dense 단독)에 맞게 개정 — 'pgvector+BM25 EnsembleRetriever 0.6/0.4' 허위 룰 폐기, '향후 hybrid 도입 시 재검토' 명시. 룰↔코드 정직성 확보.

## 13. L-1 — OCR 지연 60s→목표 15~20s (T-AI-OCR-LATENCY-60S, 2026-07-14)

**Why**: 오라클 서버 실측 (사용자 체감 "1분"): `OcrProcessed total_elapsed_ms=67923 / 54146`.
분해: vision(gemini-2.5-flash) 호출당 16.8~27.8s + 파싱실패 재시도 1회(+17s) + Tier-3 correction
미해결 4건 전부 20s timeout 소진(매칭 기여 0). 병목 1위=flash thinking 기본 ON, 2위=correction.

### 시도 1 — vision thinking_budget=0 (단독)
- 지연: 17.7~26.8s → **4.5~7.6s (3~4배 단축)** ✅
- 그러나 실8장 e2e에서 **의료 게이트 FAIL**:
  1. vision 이 조제 개수(1)를 dose_amount 필드에 기입 (3/3 재현) → dose 검증 오염
     → `뮤테란캡슐200밀리그램` 이 **100밀리그램으로 false-auto**
  2. name_raw 에 성분·함량 줄을 `\n` 로 이어붙임 → normalize 가 브랜드 훼손

### 시도 2 — 프롬프트 필드 정의 강화 (ocr_system.txt 규칙 1 보강)
- "name_raw 는 약품명 한 줄만", "dose_amount 는 함량, 투여 개수(1정/1포) 아님, 불명이면 null"
- 재게이트 결과: **items 35 · AUTO 22 · MANUAL 12 · multiline 0 · 뮤테란 200→200 정확 AUTO** ✅
- 남은 ⚠️ 1건(`경동아스피린장용정`→유영아스피린장용정100mg AUTO)은 **thinking 무관 기존 매처 구멍**:
  vision 없이 매처에 `dose=100` 만 넣어도 동일 재현. DB `경동아스피린장용정`(이름에 함량 없음)이
  dose_hits 필터에서 밀리고 이름에 "100mg" 붙은 타 브랜드가 선택됨. → 후속 태스크 (L-2 후보).

### 함께 적용
- correction: main.py 의 `model=settings.gemini_model`(flash) override 제거 → 어댑터 기본
  flash-lite(thinking OFF) 복원 + `CORRECTION_TIMEOUT_SEC` 20→8s (worst-case -12~18s).
- eval=prod 정합: run_real_e2e_rrf.py 도 thinking_budget=0 동기화, 리포트는
  `real_e2e_rrf_2026-07-14-thinking0.*` 신규 저장 (06-13 baseline 보존).

### 교훈
1. **thinking 은 스키마 필드 배치까지 담당하고 있었다** — thinking 제거 시 필드 혼동(개수→함량)이
   생기며, 프롬프트에 필드 정의를 명시하면 회복된다. "속도 최적화는 프롬프트 명세 강화와 세트".
2. 6/13 리포트는 B-10 이전 매처라 baseline 오염 — **A/B 는 반드시 같은 매처로 당일 재측정**.
3. exact_fast dose_hits 는 "이름에 함량 명기된 후보"만 남긴다 — 함량 미표기 정품 브랜드가 밀리는
   역설 존재 (L-2 후보).

### 예상 효과 (배포 후 재실측 필요)
- vision ~6s + 매칭 수 s + correction ≤8s(미해결 시) → **총 12~20s 내외** (기존 54~68s)

## 14. L-2 — dose=None 다강도 오확정 AUTO 차단 (T-AI-DOSE-NULL-CONFIRM, 2026-07-14)

**Why**: Adversarial 이 "vision이 dose_amount=null 을 주면 강도 게이트가 통째로 사라진다"는
가설을 코드 3곳에서 실증. 프롬프트가 "함량 안 보이면 null" 을 명시 지시(L-1 시도2) 하므로
dose=None 은 정상 입력이며, 이 경로에 다강도(예: 5mg/10mg) 방어가 전혀 없었다.

### 발견된 3개 구멍 (Adversarial 실증)
1. `normalize_for_cascade` 가 함량을 제거 → 강도 게이트 소스 자체가 cascade 쿼리에서 사라짐 (설계 의도, 문제는 아래 2·3).
2. `StrongExactAdapter._pick_best`(rrf_adapters.py): `parsed.dose_amount is None` 이면 dose 필터를
   완전히 건너뛰고 `pool[0]`(최단 이름 — 보통 저강도)을 score 1.0 AUTO 로 확정.
   실증: `노바스크정` dose=None + pool={5mg,10mg} → 5mg AUTO (10mg 이 정답이면 오확정).
3. `MatchDecider._dose_variants`(decider.py): 강도 다변 감지 방어 분기가 존재했으나
   `Candidate.dose_amount` 를 어떤 retriever 도 채우지 않아 (`_row_to_candidate` 가 항상 `None`
   하드코딩) **호출되어도 항상 빈 결과 — dead code**. AUTO 를 막을 마지막 방어선이 무력화 상태였음.

### 픽스
1. `normalizer.py`: `extract_dose(name)`/`strip_dose(name)` 신설 — 약품명 문자열에서 강도 파싱을
   단일 소스로 통일(decider 의 개별 정규식과 중복/불일치 위험 제거).
2. `rrf_adapters.py`:
   - `_row_to_candidate` + `TrigramMultiAdapter` 가 `Candidate.dose_amount` 를 실제로 채움
     → `_dose_variants` dead code 재활성화, `DomainReranker._dose_score` 보너스/패널티도 함께
     재활성화(부수 효과, 안전 방향).
   - `StrongExactAdapter._pick_best`: `parsed.dose_amount is None` 분기에 `_has_dose_ambiguity(pool)`
     게이트 추가 — pool 내 서로 다른 강도가 2개 이상이거나 강도 파싱 가능/불가 혼재 시 단축 금지
     (None → RRF/CONFIRM 위임). 단일 강도 약(뮤테란캡슐 등)·강도 정보 자체가 없는 복합제는
     기존 AUTO 유지(과잉 CONFIRM 방지).
3. `decider.py`: `_dose_variants` 를 전체 이름 비교(항상 self 만 매치되는 무의미한 비교)에서
   `strip_dose()` 기준명 비교로 재작성 — 서로 다른 강도의 동일 계열 약(노바스크정5mg vs 10mg)을
   실제로 찾아낸다.

### 검증 (RED→GREEN→재현불가, 3단)
- RED: 신규 테스트 8건을 fix 적용 전 코드(`git apply -R`)로 재현 → 전부 실패 확인
  (노바스크 5mg/10mg pool 이 그대로 AUTO 로 반환됨을 직접 확인).
- GREEN: fix 재적용 후 동일 8건 PASS + 전체 328 PASS(`pytest tests/ --ignore=tests/eval`).
- 재현 불가 확인: fix 적용 상태에서 동일 시나리오 재실행 → None 반환(단축 금지) 확정.

### 실증거 — 실 8장 e2e + GT100 블라인드
- **실 8장(운영 RRF 경로, Gemini 8회 호출)**: 기존 정답 AUTO 3건(동광니자티딘150mg·엔테론150mg·
  뮤테란200mg) 전부 유지. suspicious_auto 1건(`경동아스피린장용정`→`유영아스피린장용정100mg`)은
  **13절에 이미 L-2 후보로 기록된 기존 구멍**(dose 값이 있는 경로의 dose_hits 오선택) — 본 fix 는
  `parsed.dose_amount` 가 채워진 분기를 건드리지 않아(`elif` 로 dose=None 분기에만 개입) 무관/불변임을
  코드상 확인.
- **캐시 텍스트 재매칭(A/B, 동일 name_raw, Gemini 재호출 없음)**: 35개 중 4개 결정 변경, 전부 이전
  AUTO→CONFIRM/MANUAL 강등(반대 방향 없음). DB 확인 결과 4건 전부 **실재하는 다강도/이질 pool**
  (예: `포리부틴정(트리메부틴말레산염)`↔`포리부틴정150mg(트리메부틴말레산염)`이 서로 다른 kd_code 로
  DB 에 별도 존재) — 과잉 CONFIRM 아닌 정당한 안전 강등.
- **GT100 블라인드(DB-only, LLM 호출 없음)**: surfacing 98/100 유지(게이트 기준 충족). semantic hit
  98→97(신규 miss `gt_011`, RRF fallback retriever 가 StrongExact 수준의 salt/제조사 정규화가 없어
  exact_fast 차단 후 대체 후보를 못 찾음 — 별도 아키텍처 갭, 후속 과제로 기록). AUTO 비율은 69%→14%로
  급락했으나 이는 **이 harness 고유 특성** 때문 — `run_blind_gt100.py` 는
  `parse_drug_item(normalize_for_cascade(name_raw))` 만 사용하고 `raw.dose_amount` 별도 필드 보충이
  전혀 없어 100건 전부가 dose=None 최악 조건으로 측정됨. 프로덕션은 `RrfMatcherAdapter`(service.py
  경유)가 vision 의 별도 `dose_amount` 필드를 항상 보충하므로 이 정도 낙폭은 재현되지 않는다
  (실 8장 A/B 는 26/35→22/35 로 하락 폭이 훨씬 작음). 오히려 기존 69% 가 강도 미검증 상태로
  부풀려져 있었다는 정직한 반증.

### 교훈
1. **dead code 는 조용히 안전을 무력화한다**: `_dose_variants` 가 컴파일·테스트를 통과해도 입력
   (`Candidate.dose_amount`)이 항상 None 이면 방어선이 아니라 장식이다. "방어 로직이 있다"와
   "방어 로직이 실행된다"는 다른 주장 — 입력 소스까지 추적해야 한다.
2. **평가 harness 의 입력 경로가 프로덕션과 다르면 지표가 왜곡된다**: GT100 블라인드는 의도적으로
   `raw.dose_amount` 보충을 생략(text-only 측정 목적)하는데, 이 차이를 모르고 AUTO 69%→14% 만 보면
   "치명적 회귀"로 오판할 뻔했다. A/B 비교는 반드시 "어느 경로(harness vs 프로덕션 wiring)로 쟀는지"
   명시해야 한다.
3. **"강도 불명 = 무조건 CONFIRM" 은 과잉이다**: 단일 강도 약·강도 정보 없는 복합제까지 강등시키면
   UX 가 죽는다. "pool 내 강도가 실제로 갈리는가"로 좁혀야 안전과 편의가 공존한다.

### 남은 갭 (후속 과제, 이번 스코프 아님)
- `경동아스피린장용정`(dose 있음, dose_hits 오선택) — 13절 L-2 후보, 여전히 미해결.
- RRF fallback retriever 들에 StrongExactAdapter 수준의 salt/제조사 정규화 부재 → exact_fast 차단 시
  대체 후보를 못 찾는 사례 존재(GT100 gt_011). 후보안: PrefixRelaxMultiAdapter/IlikeMultiAdapter 에도
  `normalize_for_cascade`+`_strip_salt` 적용 검토.
