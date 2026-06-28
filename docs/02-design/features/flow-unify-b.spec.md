# T-FLOW-UNIFY-B — 처방전 등록 흐름 B방식 통일 (카메라도 검토 후 명시 등록)

작성일: 2026-06-28
사용자 명시: "카메라로 사진 찍는 것도 b방식이 나은 것 같은데"

## 진단

현재 처방전 등록 흐름이 두 갈래로 분기:

### A. 카메라 (자동 등록)
```
camera.tsx → POST /prescriptions/ocr (OCR + 자동 등록) → confirm.tsx → result/[id].tsx
```
- ⚠️ OCR 결과 = 자동 DB 등록. 사용자가 confirm에서 취소해도 처방 잔존 (dangling)
- ⚠️ medical-safety 룰: "신뢰도 낮은 OCR 결과 자동 등록 금지" 우회 위험
- confirm.tsx 역할 모호 (이미 등록됐는데 "확인" 단계)

### B. 갤러리/수동 (검토 후 명시 등록)
```
scan.tsx / manual.tsx → POST /prescriptions/ocr/extract (추출만) → review.tsx (편집) → POST /prescriptions (명시 등록) → home
```
- 사용자가 약 정보 검토/편집/추가 가능
- 등록 안 하면 DB에 없음 (clean)
- review.tsx에 슬롯/기간/시간 picker 등 완비

## CTO 결정

**B방식으로 통일 = 카메라도 OCR만 하고 review.tsx에서 검토 후 명시 등록**.

근거 (이미 사용자/CTO 합의):
- 의료 안전 P0 룰 부합
- 데이터 정합 (dangling row 0)
- 코드 단순화 (`/ocr` endpoint deprecate, confirm.tsx 삭제)
- 일관성 (3 입력 방식 동일 패턴)

## 절대 규칙 (재확인)

- TDD: 변경된 분기 회귀 테스트
- DDD 의존 역전 X
- 의료 안전 graceful 유지
- DB-safety: 기존 `prescriptions` 행 DELETE/TRUNCATE 0 (마이그레이션 X, 코드만)
- git commit/push 금지 (CTO 단독)
- clean-code: SRP, 매직넘버 X
- no-overengineering: 기존 review.tsx 재사용, 신규 컴포넌트 X

---

## FE-Dev 작업

### 1. camera.tsx 흐름 변경
파일: `front/src/app/prescription/camera.tsx`
- L56 `prescriptionApi.ocr(...)` → `prescriptionApi.ocrExtract(...)` 로 교체 (scan.tsx와 동일 API)
- L62 `router.replace('/prescription/confirm')` → `router.replace('/prescription/review')` 로 교체
- OCR 응답 처리: `addFromOcr(ocrResp)` 는 그대로 (slice에서 items 채움)
- 변수명/상수 정리 (자동 등록 가정 코드 제거)

### 2. confirm.tsx 처리
파일: `front/src/app/prescription/confirm.tsx`
- 삭제 권장 (아무도 navigate 안 함)
- 또는 deprecation: 빈 placeholder 후 review로 redirect (backward compat 짧게)
- 결정: **즉시 삭제** (워킹트리 검토 + 다른 곳에서 참조 grep 0 확인 후)

### 3. result/[id].tsx 정리
파일: `front/src/app/prescription/result/[id].tsx`
- 현재: 카메라 자동 등록 후 결과 화면
- 변경 후: review.tsx 등록 성공 → `/(tabs)/home` 라우팅 (이미 review.tsx L199 적용)
- result/[id].tsx 사용 위치 grep 후 deprecate 가능 시 삭제, 사용처 있으면 다른 라우팅 분기 정리

### 4. 테스트
- `front/tests/unit/cameraFlow.test.tsx` (NEW 또는 갱신) — camera.tsx가 ocrExtract 호출 + review로 라우팅 검증
- confirm.tsx 테스트 있으면 삭제
- 통합 E2E (수동 검증): 카메라 → review → 등록 → home 흐름

### 5. FE 보고
`.cmux/messages/cto/inbox/T-FLOW-UNIFY-B-fe-done.json`
포함: 변경 파일 + jest/tsc + git status + 삭제/유지 결정 사유

---

## BE-Dev 작업

### 1. POST /prescriptions/ocr endpoint 처리
파일: `back/app_server/.../prescription/presentation/PrescriptionController.java`
- `@PostMapping("/ocr")` endpoint:
  - **옵션 A (권장)**: 삭제 — FE에서 더 이상 호출 안 함 (camera.tsx 변경 후)
  - **옵션 B**: `/ocr/extract` 와 동일 동작으로 단순화 (OCR만 수행, 등록 X) — backward compat
- 옵션 A 선택: 관련 UseCase / Service 메서드도 정리 (`OcrAndRegisterPrescriptionUseCase` 같은 게 있다면 삭제)

### 2. RegisterPrescriptionService 정리
파일: `back/app_server/.../prescription/application/RegisterPrescriptionService.java`
- 자동 등록 분기 제거 (있다면)
- 명시 등록(`POST /prescriptions`) 단일 경로로 단순화
- `Prescription.create()` factory에 `ocrStatus` 인자 정리 (자동 vs 검토 후 분기 제거)

### 3. (선택) 기존 dangling 처방 정리
- ocrStatus 가 DONE이지만 사용자가 confirm 안 한 케이스 → soft delete? 본 task는 신규 코드만 (기존 데이터 무영향)
- 별도 task로 분리 권장 (DB-safety 룰 명시 동의 필요)

### 4. 테스트
- `PrescriptionControllerTest`: `/ocr` 삭제 시 관련 테스트 삭제
- `RegisterPrescriptionServiceTest`: 자동 등록 분기 테스트 삭제 또는 갱신
- ArchUnit 통과 유지

### 5. BE 보고
`.cmux/messages/cto/inbox/T-FLOW-UNIFY-B-be-done.json`
포함: 변경 파일 + ./gradlew test + ArchUnit + git status

---

## 상호 의존성

- FE camera.tsx 변경 → BE `/ocr` endpoint 더 이상 호출 안 됨 (FE 먼저 머지 후 BE deprecate 가능)
- 같은 PR/커밋에 묶어 머지 권장 (FE B + BE A 동시)

## 인수 기준

1. 카메라 촬영 후 review.tsx 로 진입 (confirm.tsx 미경유)
2. review.tsx에서 "등록하기" 누르면 정식 등록 (현재 갤러리/수동과 동일)
3. POST /prescriptions/ocr endpoint 미존재 (404) 또는 deprecated 응답
4. 기존 처방전 데이터 무영향 (DB SELECT 회귀 없음)
5. 모든 테스트 PASS (BE/FE/AI)
6. confirm.tsx 파일 제거 또는 빈 placeholder

## 비-범위

- 기존 dangling 처방 정리 (별도 task, DB-safety 명시 동의 필요)
- review.tsx UI 재설계 (이미 정리됨, 별도 spec)
- OCR 비동기 + polling (Phase 2~3 검토)
- BE Soft Delete 도메인 메서드 추가 (별도 검토)
