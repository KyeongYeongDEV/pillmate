---
name: ocr-recipe
description: 처방전 사진 업로드부터 약 자동 등록까지의 전체 파이프라인을 단계별로 검증하는 워크플로우.
---

# OCR Recipe

## 흐름

```
[Client]
   ↓ ① POST /prescriptions/upload-url
[Spring Boot] presentation → application → infrastructure (S3 Pre-signed URL)
   ↓
[Client] PUT (S3 직접 업로드)
   ↓ ② POST /prescriptions/{id}/process
[Spring Boot] → [FastAPI] /ocr/prescription
   ↓                ↓
   ↓                Gemini Vision (cache check)
   ↓                ↓
   ↓                pgvector Hybrid Retrieval
   ↓                ↓
[FastAPI] 매칭 결과 (drug_code, confidence) 반환
   ↓
[Spring Boot] PrescriptionAggregate 저장 (TDD로 테스트된 도메인 로직)
   ↓ ③ Schedule 자동 생성 (기본 시간대)
[Client] 처방전 등록 완료
```

## 단계별 체크리스트

### ① Pre-signed URL 발급
- [ ] TTL 5분
- [ ] PUT 한정 (GET/DELETE 금지)
- [ ] Content-Type 검증 (image/jpeg, image/png)
- [ ] 파일 크기 한도 (10MB)
- [ ] 객체 키는 UUID (환자 식별자 포함 금지)

### ② OCR 처리
- [ ] 이미지 해시 캐시 hit 확인 (Redis `ocr:{sha256}`)
- [ ] Gemini Vision 호출 (gemini-2.5-flash)
- [ ] JSON 스키마 강제 (`schemas/ocr-extract.json`)
- [ ] Hybrid Retrieval (BM25 + Dense)
- [ ] 신뢰도 < 0.7 → 사용자 확인 필요 플래그

### ③ 도메인 등록
- [ ] PrescriptionAggregate 트랜잭션 (UseCase 레이어)
- [ ] PrescribedDrug N개 batch insert
- [ ] Schedule 자동 생성 (기본 시간대: 08:00, 12:30, 18:30, 22:00)
- [ ] PrescriptionRegistered 도메인 이벤트 발행

## 실패 시나리오 테스트 (TDD)

| 시나리오 | 기대 동작 |
|---------|-----------|
| 이미지 크기 초과 | 413 Payload Too Large |
| Gemini 타임아웃 | Circuit Breaker → 수동 입력 fallback |
| 약품 매칭 신뢰도 < 0.5 | "약을 인식하지 못했습니다" + 수동 등록 |
| 식약처 DB 없는 약 | 사용자 확인 단계로 |
| 중복 처방전 (해시 일치) | 캐시 hit, 빠른 응답 |

## 비용 가드

- 일 사용자당 OCR 5회 제한
- 동일 해시 재요청은 비용 0 (Redis hit)
- 월 OCR 비용 > $5 → 알람

## 참조

- `agents/prescription-ocr-expert.md`: OCR 에이전트
- `agents/rag-curator.md`: RAG 큐레이터
- `schemas/ocr-extract.json`: OCR 응답 스키마
