# Phase B-6 Post-Raw-Quality Evaluation Report

**날짜**: 2026-06-09  
**작업**: T-AI-OCR-RAW-QUALITY  
**목표**: OCR raw 정확도 ↑ (88.57% → 94~96%)

---

## 변경 요약

| 변경 | 내용 |
|------|------|
| `ImagePreprocessor` 신규 | EXIF 회전 / Hough deskew / CLAHE / bilateral denoise / resize |
| Few-shot 프롬프트 강화 | 10가지 케이스 예시 (system_prompt.txt) |
| Feature flags | `PREPROCESS_ENABLED` (default True), `FEWSHOT_ENABLED` (default True) |
| OcrService 통합 | Gemini Vision 호출 전 preprocess 분기 |

---

## 테스트 결과

### 단위 테스트 (ImagePreprocessor)

| 테스트 그룹 | 건수 | 결과 |
|------------|------|------|
| rotate_by_exif | 3 | ✓ PASS |
| deskew | 3 | ✓ PASS |
| enhance_contrast | 3 | ✓ PASS |
| denoise | 2 | ✓ PASS |
| resize_if_large | 3 | ✓ PASS |
| 전체 파이프라인 | 1 | ✓ PASS |
| **합계** | **15** | **15/15 PASS** |

### GT 100건 회귀 테스트

```
GT 100건 Hit@1: 97/100 = 0.970 (회귀 없음) ✓

Easy   30건: 1.000 (불변)
Medium 44건: 0.955 (불변)
Hard   26건: 0.962 (불변)

기존 miss 3건 (변경 없음):
  gt_016: '리오노필정' → DB 미수록
  gt_039: '아스피링정' → prefix collision precision 이슈
  gt_092: '에소메프라조를' → 심각한 OCR 타이핑 오류
```

### 전체 단위 테스트 (비-eval)

```
155 passed, 16 deselected — 회귀 없음 ✓
```

---

## Ablation 분석

### 각 컴포넌트 기대 효과 (분석적 추정)

**Tier 0 (Phase B-4)**: preprocessing cascade
- 제조사 prefix 제거 + jamo prefix_match → +5.7pp (88.57% → 94.27% 분석)

**Phase B-6 추가 컴포넌트**:

| 컴포넌트 | 타깃 케이스 | 예상 효과 |
|---------|-----------|---------|
| EXIF 회전 보정 | 스마트폰 촬영 세로/가로 혼용 | 촬영 방향 오류 제거 |
| deskew (Hough) | 기울어진 처방전 (±5°) | 경사 보정 → 텍스트 인식 ↑ |
| CLAHE 대비 향상 | 어두운/저대비 처방전 | 흐린 글자 인식 ↑ |
| bilateral denoise | 노이즈 많은 복사본 처방전 | 노이즈 억제 → 경계 선명 |
| resize (max 1920px) | 고해상도 (4K+) 이미지 | API 비용 ↓ + 처리 속도 ↑ |
| Few-shot 프롬프트 | confidence 결정, candidates 품질 | 더 정확한 candidates 제공 |

### Few-shot 효과 특이사항

- **confidence 보정**: 예시 없을 때 모델이 경계 케이스에서 과신(overconfident) 경향
  → Few-shot 예시 후 0.6~0.7 경계에서 더 정확한 분류 기대
- **candidates 품질**: 오인식 예시(예시 3) 참고 후 더 의미 있는 후보 생성 기대
  → "쎌박타민정" → ["썰박타민정500밀리그램", ...] 패턴 학습

### 실 이미지 재측정 계획

Gemini API 일일 할당량(20 RPD) 관계로 실 8장 재측정은 다음 날(00:00 UTC 리셋) 예정:

```
예상 before/after:
  B-5 이전 (88.57%) → B-6 이후 목표 (94~96%)

측정 방법: tests/eval/run_eval_full.py --real-images
대상: back/ai_server/tests/eval/sample_images/*.jpg (8장)
```

---

## 구현 커밋

| # | hash | 설명 |
|---|------|------|
| 1 | 359b86b | Test(RED): ImagePreprocessor 15건 |
| 2 | 340f2ef | Feat: ImagePreprocessor OpenCV 구현 |
| 3 | 20cedba | Refactor: OcrService preprocess 분기 + flags |
| 4 | 7bd311d | Feat: Few-shot prompt 10건 + vision.py 통합 |

---

## 비용 영향

| 항목 | Before | After |
|------|--------|-------|
| preprocess latency | 0ms | +200~500ms (cv2 처리) |
| 이미지 크기 (4K 기준) | ~8MB | ~400KB (1920px) → 토큰 ↓ |
| few-shot 프롬프트 증가 | 기본 (~400 토큰) | +~600 토큰 (few-shot 10건) |
| 실 API 비용 증가 | — | +$0.0001/요청 추정 (negligible) |

→ 이미지 resize 로 인한 토큰 절감이 few-shot 추가분을 상쇄.

---

## 다음 단계

1. **실 8장 재측정** (Gemini API 00:00 UTC 리셋 후)
2. **T-AI-RAG-HNSW**: ivfflat → HNSW 전환 (recall +3~7%)
3. **T-BE-POST-/DRUGS/ALIAS**: alias endpoint (현재 404)
4. **deskew 튜닝**: 실 이미지 skew 분포 측정 후 threshold 최적화
