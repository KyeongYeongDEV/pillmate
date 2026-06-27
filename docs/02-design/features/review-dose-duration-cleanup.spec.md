# T-REVIEW-DOSE-DURATION-CLEANUP — 약 검토 화면 1회복용량 제거 + 복약기간 자유입력

작성일: 2026-06-27
사용자 명시:
1. "약 검토에서 1회복용량 이거는 없애"
2. "복약기간도 최소가 7일은 너무 길어 오늘을 기준으로 며칠 작성할 수 있게 하고 무기한도 있도록 해"

## 배경

- `front/src/app/prescription/review.tsx` 가 OCR 이후 검토·등록 화면
- `DrugCard.tsx:93-95` 에 약 카드별 "1회 복용량" UI (- / + stepper) — 사용자 보기엔 불필요
- `DURATION_PRESETS = ['7일','14일','30일','90일','무기한']` 가 chip preset 만 제공 — 1~6일 같은 짧은 기간 입력 불가
- BE 는 `RegisterPrescriptionRequest.items[].doseAmount` + `schedule.startDate/endDate` 받음 → FE만 UI 변경, BE 영향 최소

## 절대 규칙 (재확인)

- BE 변경 X (이번 변경은 FE only)
- TDD: 신규 입력 컴포넌트 + slice 액션 변경 시 테스트
- clean-code: SRP, 매직넘버 상수화 (MIN/MAX duration)
- no-overengineering: 신규 picker 라이브러리 추가 X (TextInput 또는 기존 컴포넌트 재사용)
- 의료 안전: 0일/음수 차단, 비현실적 최댓값 차단 (예: 365일 cap)

---

## FE-Dev 작업

### 1. 1회 복용량 UI 제거

#### 1.1 DrugCard.tsx
파일: `front/src/components/prescription/DrugCard.tsx`
- L93~95 "1회 복용량" 라벨 + stepper 블록 삭제
- 관련 props 정리: `onDoseChange` 등 unused 시 props/import 정리
- 카드 디자인 토큰 유지 (gap/spacing 자연스럽게)

#### 1.2 review.tsx
파일: `front/src/app/prescription/review.tsx`
- `<DrugCard>` 호출부에서 `onDoseChange={...}` prop 제거 (DrugCard signature 변경 시)
- `updateDoseAmount` dispatch 호출 0건 확인 (스텝퍼 자리 사라짐)

#### 1.3 prescriptionFlowSlice.ts
파일: `front/src/store/slices/prescriptionFlowSlice.ts`
- `doseAmount` 필드 유지 (BE 페이로드에 default `1` 전송 — 호환성)
- `updateDoseAmount` 액션은 유지 (manual.tsx 등 다른 곳에서 사용 가능 — grep 후 미사용 시 제거 검토)
- 신규 약 추가 시 `doseAmount: 1` default 그대로

#### 1.4 PrescriptionDrugRow.tsx (상세 화면 표시 — 변경 X)
- 상세 화면의 `doseAmount` 표시(`X정` 또는 `용량 미상`)는 그대로 유지 (사용자 의도는 검토 화면의 stepper UI만 제거)

### 2. 복약 기간 자유 입력

#### 2.1 DURATION_PRESETS 변경
파일: `front/src/app/prescription/review.tsx` (L83) + `front/src/app/prescription/manual.tsx` (L28)
- 변경: `['3일','7일','14일','30일','무기한']` 또는 사용자 직접 입력 + 무기한
- 권장 UX (사용자 명시 "오늘 기준 며칠"):
  ```
  [TextInput 일수] 일 + [무기한 toggle 체크박스]
  + 빠른 선택 chips: [3일][7일][14일][30일][90일]
  ```
- 또는 단순 chips + 직접 입력 한 자리 통합

#### 2.2 유효성
- 입력값 정수, **MIN_DURATION_DAYS = 1**, **MAX_DURATION_DAYS = 365** 상수
- 0/음수/문자 입력 차단 (placeholder "예: 5")
- 무기한 선택 시 `endDate = null` 전송 (BE 이미 nullable 처리됨, 기존 behavior 유지)
- 일수 선택 시 `endDate = addDays(startDate, days - 1).toISOString().slice(0,10)` (이미 기존 로직 있음 추정)

#### 2.3 review.tsx L346~365 (복약 기간 섹션)
- chip 5개 → 직접 입력 TextInput + 무기한 toggle + 빠른 선택 chips (preset 갯수 줄여도 OK)
- 디자인 토큰: `typography.body2n`, `colors.labelNormal`, `space.s8`, `radius.r12`
- 키보드: `keyboardType="number-pad"`, `returnKeyType="done"`

### 3. 테스트

- `front/tests/unit/prescriptionFlowSlice.test.ts` — `updateDoseAmount` 테스트가 있다면 그대로 유지 (BE 호환), 또는 unused 확인 후 제거
- 신규: `front/tests/unit/durationInput.test.tsx` (또는 review 화면 분기 테스트) — MIN/MAX/무기한/유효성

### 4. 인수 기준

1. 검토 화면(review.tsx) 약 카드에 "1회 복용량" 라벨/stepper 미노출 (스냅샷 또는 UI 확인)
2. 복약 기간 섹션에서 1일 ~ 365일 직접 입력 가능 + 무기한 토글
3. 등록 페이로드 `items[].doseAmount = 1` (기본값 강제 전송), `schedule.endDate = null (무기한)` 또는 ISO 날짜 (일수 입력 시)
4. 0/음수/365 초과 차단
5. jest + tsc 0 에러
6. manual.tsx 화면도 동일 정책 (자유 입력 + 무기한)

### 5. 보고

작업 완료 시: `.cmux/messages/cto/inbox/T-REVIEW-DOSE-DURATION-CLEANUP-fe-done.json`
포함: 변경 파일 + jest/tsc 결과 + 스크린샷 권장 + git status (워킹트리만)

## 비-범위

- BE schedule.slots / customTime 변경 X
- 약 카드 디자인 전면 개편 X (1회 복용량 제거만)
- DatePicker 라이브러리 도입 X
