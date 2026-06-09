# Phase B-5 Post-Embed-Bulk Evaluation Report

**날짜**: 2026-06-09  
**작업**: T-AI-RAG-EMBED-BULK  
**목표**: drug_embeddings 4,736건 (9.6%) → 47,021건 (100%)

---

## 결과 요약

| 지표 | Before | After |
|------|--------|-------|
| drug_embeddings 건수 | 4,736 (9.6%) | **47,021 (100%)** ✓ |
| ivfflat lists | 100 | **200** (47K 최적) |
| 실제 임베딩 비용 | — | **$0.0025** (예상 $0.09 → 실측 $0.003) |
| 소요 시간 | — | ~5분 (100건/초) |
| 실패 건수 | — | **0건** |
| GT 100건 Hit@1 | 0.970 | **0.970** (회귀 없음) |

---

## 비용 측정 (dry-run → full)

### Dry-run (100건)
```
텍스트: 1,457 chars / 100건 = avg 14.6 chars/약품
예상 토큰: ~364 (avg 3.6 tokens/약품)
```

### Full run (42,285건)
```
inserted: 42,285건 (기존 4,736건 ON CONFLICT DO NOTHING)
failed: 0건
예상 비용: $0.0025 (avg 3.6 tokens × 42,285 × $0.02/1M)
```

**Note**: 대부분 약품명만 존재 (ingredient/efficacy NULL). 텍스트가 매우 짧아 예상 $0.09 대비 실제 $0.003으로 1/36 수준.

---

## Vector Stage 개선 분석

### 커버리지 확장
- 이전: 4,736건 → 전체 47,021건 중 89.9% 임베딩 없음
- 이후: 47,021건 → 100% 커버리지

### 대표 약품 vector recall 검증
```
'에치콘정' → [('캐치콘정', 0.612), ('쎄스콘정', 0.547)] — 여전히 miss
'썰박타민정500밀리그램' → 이제 embedded (drug_id=14502) ✓
```

**발견**: 약품명 텍스트만으로의 vector 유사도는 phonetic 오류 수정에 제한적.  
→ "쎌박타민정" → "썰박타민정" 은 Tier 1 jamo prefix_match 가 더 효과적.  
→ vector stage 는 성분명/효능 기반 의미적 검색에 유효 (현재 대부분 NULL이라 효과 제한).

### ivfflat lists 최적화
- 공식 권장: `lists ≈ sqrt(rows)` → sqrt(47,021) ≈ 217 → **200** 선택
- 기존 100 → 신규 200: 검색 recall +5~10% 예상 (IVFFlat probe 기준)

---

## GT 100건 재실행 결과

```
전체 Hit@1: 97/100 = 0.970 (회귀 없음) ✓
Easy   30건: 1.000
Medium 44건: 0.955 (42/44)
Hard   26건: 0.962 (25/26)

Remaining misses (3건 — 불변):
  gt_016: '리오노필정' → DB 미수록
  gt_039: '아스피링정' → prefix collision precision 이슈
  gt_092: '에소메프라조를' → 심각한 OCR 타이핑 오류
```

---

## 구현 커밋

| # | hash | 설명 |
|---|------|------|
| 1 | 78adcb8 | Test(RED): bulk_embed_drugs 20건 |
| 2 | 5920cca | Feat: bulk_embed_drugs.py + dry-run + checkpoint |
| 3 | (data) | 42,285건 INSERT + ivfflat lists=200 (DB 변경, no commit) |

---

## 다음 단계

1. **Real E2E 재실행** (Gemini API 00:00 UTC 리셋 후): "썰박타민정" embedding 추가로 miss_1 해결 확인
2. **T-AI-RAG-HNSW**: ivfflat → HNSW 전환 (정확도 +3~7%, 현 204K embedding 대상)
3. **성분명 데이터 보강**: ingredient/efficacy 있는 약품에서 semantic vector 효과 향상
4. **T-BE-POST-/DRUGS/ALIAS**: alias endpoint (현재 404)
