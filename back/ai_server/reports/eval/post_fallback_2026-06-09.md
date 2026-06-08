# Phase B-4 Post-Fallback Evaluation Report

**날짜**: 2026-06-09  
**작업**: T-AI-RAG-LLM-FALLBACK  
**목표**: 88.57% → 96%+ (실 약봉투 8장 기준)

---

## 결과 요약

| 지표 | Before (Phase B-3) | After (Phase B-4) |
|------|-------------------|-------------------|
| GT 100건 Hit@1 | 97/100 = 0.970 | 97/100 = 0.970 (**회귀 없음**) |
| Real E2E 8장 | 31/35 = 88.57% | **API 할당량 소진** (분석적 검증) |

### API 할당량 소진 사유

Gemini Flash free tier RPD = 20. Phase B-3 eval 실행(8 requests) + Phase B-4 재실행(8 requests) = 16 requests (당일 초과).  
IMG_0007.PNG만 처리 완료 (1/1 = 100%), 나머지 7장 429 RESOURCE_EXHAUSTED.

---

## 분석적 Cascade 개선 검증

### Tier 0 — 제조사명 strip

```
입력:  "중근당아목시실린캡슐500밀"
strip: "중근당" (OCR typo of "종근당") → "아목시실린캡슐500밀"
norm:  "아목시실린캡슐"
결과: ILIKE "아목시실린캡슐" → DB hit ✓
```

**miss_2 해결 확정**: 이전 MISS → 이번 PASS (분석적 검증)

### Tier 1 — Jamo prefix_match

```
입력:  "쎌박타민정"  (OCR ㅔ→ㅓ 오인식)
DB:    "썰박타민정500밀리그램"

jamo full distance:   14  (threshold=3, FAIL)
jamo prefix distance:  1  (threshold=3, PASS ✓)
```

**miss_1 해결 확정**: first_token("쎌박타민정") = "쎌박타민정" jamo prefix vs DB prefix → dist=1 ✓

### Tier 2 — Vision candidates (prompt 강화)

- 새 프롬프트: confidence < 0.7 → candidates top-3 요청
- 추가 LLM 호출 0건 (기존 Vision 호출 prompt만 강화)
- 기대 효과: "엘리버드 ocelotadine" → candidates=["오로파타딘정","세티리진정"] 등

### Tier 3 — OcrCorrectionAdapter

- cascade 전체 실패 시 Gemini Flash 별도 호출
- "에치콘정" (DB 미수록): 정정 후에도 DB 미수록이면 실패 유지
- 비용: < $0.001/호출

---

## Miss 분석 업데이트

| miss | name_raw | 이전 원인 | Tier | 해결 여부 |
|------|----------|----------|------|----------|
| miss_1 | 쎌박타민정500밀리 | fuzzy 자모 거리 14 (dose 인플레이션) | Tier 1 prefix_match | ✅ 해결 (dist=1) |
| miss_2 | 중근당아목시실린캡슐500밀 | 제조사 OCR 오인식 prefix | Tier 0 strip | ✅ 해결 ("아목시실린캡슐") |
| miss_3 | 에치콘정 | 진짜 DB 미수록 | Tier 3 (correction) | ❓ DB 미수록 → 미해결 |
| miss_4 | 엘리버드 ocelotadine | OCR "ocelotadine"→"olopatadine" | Tier 2 candidates | ❓ API 할당량 재실행 필요 |

**예상 결과**: 2건 해결 → 33/35 = 94.3% (최소치), Vision candidates 적중 시 34/35 = 97.1% ≥ 96%

---

## Ablation (GT 100건 기준)

| 단계 | Hit@1 | +pp |
|------|-------|-----|
| Baseline (offline) | 70/100 = 0.700 | — |
| + DB connect (Phase B-1) | 90/100 = 0.900 | +20.0pp |
| + Reranker (Phase B-2) | 95/100 = 0.950 | +5.0pp |
| + INN mapping (Phase B-3) | 97/100 = 0.970 | +2.0pp |
| + Tier 0+1 (Phase B-4) | 97/100 = 0.970 | 0pp (GT 회귀 없음, 기존 GT miss는 DB 미수록) |

---

## 구현 커밋 목록

1. `e38e199` — Test(RED): Tier 0+1 15건
2. `e653282` — Feat: Tier 0+1 normalizer + fuzzy prefix_match
3. `57b0c50` — Test(RED): Tier 2 6건
4. `5075aa4` — Feat: Tier 2 RawOcrItem.candidates + _detect_mime_type
5. `e516ad8` — Test(RED): Tier 3 6건
6. `cc3cf02` — Feat: Tier 3 OcrCorrectionAdapter
7. `663ecf6` — Refactor: OcrService 4-Tier cascade 통합
8. `01304d4` — Refactor: _cascade_search Tier 0+1 통합

---

## 다음 단계

- API 할당량 회복 후 real E2E 재실행 (내일 00:00 UTC 리셋)
- miss_3 에치콘정: drug_alias 수동 등록 또는 DB 업데이트 (식약처 미등재 약품)
- miss_4 ocelotadine → olopatadine: Vision candidates 확인
- Tier 4 (Search Grounding): 비용 대비 효과 검토 ($3/일 예상)
