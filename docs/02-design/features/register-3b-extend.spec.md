# T-REGISTER-3B-EXTEND — 등록 화면 "최근 등록" 제거 + 슬롯별 알림 시간 picker

작성일: 2026-06-27
사용자 명시:
1. "약봉투 등록에서 최근등록 칸 없애"
2. "처방전 등록을 할 때 시간(FCM 푸시 알림 시간)도 정할 수 있게 해"

## 배경

- 약봉투 등록 진입 허브 화면 `front/src/app/prescription/index.tsx:163-169`에 "최근 등록" 정보성 위젯 존재 → T-HOME-AI-RECO의 홈 카드가 대체하므로 **삭제**
- BE는 이미 `RegisterPrescriptionRequest.schedule.slots[{ timeOfDay, customTime }]` 지원 (Task 3b 백엔드 부분 완료) — FE만 입력 UI 추가
- 기존 `front/src/components/schedule/TimePicker.tsx` 재사용 가능 (props `visible/initialTime/onConfirm(time:string)/onClose`, ‹› 스테퍼, 5분 라운딩, `formatTimeHHmm` export)

## 절대 규칙 (재확인)

- BE 수정 금지 (이미 BE 완료) — FE만 작업
- DB 삭제/migration 변경 X
- git commit/push X (CTO만)
- clean-code: component ≤ 적절한 라인, SRP, hooks 추출
- no-overengineering: 새 picker 라이브러리 추가 X (`TimePicker.tsx` 재사용)

---

## FE-Dev 작업

### 1. "최근 등록" 섹션 제거

`front/src/app/prescription/index.tsx`
- L163~169 "최근 등록" 섹션 JSX 삭제
- 관련 state/mock 데이터(`recents` 등 있다면) 삭제
- 관련 styles(`recentTitle/recentItem/recentDate/recentName/recentCount`) 삭제 (다른 곳에서 import 안 하는지 확인)
- 빈 자리 → 별도 채움 없이 자연스럽게 정렬 (홈 카드가 대체)

### 2. 슬롯별 알림 시간 picker

#### 2.1 데이터 모델

`front/src/store/slices/prescriptionFlowSlice.ts`
- `Slot` 타입 확장: `{ timeOfDay: 'MORNING'|'NOON'|'EVENING'|'BEDTIME', customTime?: string /* HH:mm */ }`
- `updateSlotCustomTime(timeOfDay, customTime)` 액션 추가
- 기본값 (`DEFAULT_CUSTOM_TIME`): MORNING=08:00, NOON=12:30, EVENING=19:00, BEDTIME=22:00 (`SlotToggle.tsx`의 기존 하드코딩 값 차용)

#### 2.2 등록 화면 UI

`front/src/app/prescription/confirm.tsx` (또는 등록 본화면 — Task 3b 결정 따라)
- 슬롯 토글 영역 하단에 "알림 시간" 섹션
- 활성화된 슬롯 각각에 대해 `[아침 08:00 ▾]` 형태 chip 또는 row
- 탭 시 `TimePicker` 모달 띄움 → `onConfirm`에서 `dispatch(updateSlotCustomTime(timeOfDay, time))`
- 비활성 슬롯은 picker 노출 X

#### 2.3 등록 페이로드

처방전 등록 API 호출 시 `schedule.slots[]`에 `customTime` 포함:
```typescript
{
  prescribedAt, imageKey, items,
  schedule: {
    careGroupId,
    slots: enabledSlots.map(s => ({ timeOfDay: s.timeOfDay, customTime: s.customTime })),
    startDate, endDate,
  }
}
```

### 3. Task 3b 미결 항목 결정 (CTO 결정 반영)

이전 researcher (#TASK3B-REGISTER) 4 항목 결정:

| 항목 | 결정 |
|------|------|
| `careGroupId` 출처 | BE가 JWT에서 추출 (본인 처방전 피벗) — FE는 careGroupId 전송 X. 단 BE 응답 검증 필요 |
| `bedtime` 슬롯 | FE 4슬롯 유지 (BE `DEFAULT_SLOTS` 3슬롯과 별개 — BE가 BEDTIME enum 추가 처리) — **BE-Dev 확인 요청**: BE `TimeOfDay` enum에 BEDTIME 있는지 grep 후, 없으면 enum 추가 V39 migration 필요 (별도 task) |
| `confirm.tsx` vs `result/[id].tsx` 메인 플로우 | `confirm.tsx`를 메인으로 사용 (OCR 자동 등록 후 사용자가 슬롯/기간/시간 편집 → 명시 등록) — `result/[id].tsx` 의 기존 POST 본문은 제거 |
| register endpoint 역할 분리 | OCR auto-register는 draft 상태로 저장, confirm 단계에서 명시 register로 finalize — 단 본 task는 confirm 화면 정비에 한정, draft/finalize 분리는 별도 spec (out of scope) |

**BE-Dev 확인 요청** (TimeOfDay enum BEDTIME 검증):
- `back/app_server/.../schedule/domain/model/TimeOfDay.java` (또는 동등 위치) BEDTIME 포함 여부 grep
- 없으면 BE-Dev에 enum 추가 + V39 migration spec 작성 후 별도 task 분리 (T-SCHEDULE-BEDTIME)

### 4. 테스트

- `front/tests/unit/prescriptionFlowSlice.test.ts` — `updateSlotCustomTime` 액션 검증
- `front/tests/unit/TimePicker.test.tsx` 기존 유지 (재사용 검증만)
- `front/tests/unit/confirm.test.tsx` (또는 등록 본화면) — 슬롯별 picker 렌더 + 페이로드 검증
- "최근 등록" 관련 테스트가 있었다면 삭제 또는 갱신

### 5. 보고 형식

작업 완료 시:
- 파일: `.cmux/messages/cto/inbox/T-REGISTER-3B-EXTEND-fe-done.json`
- 내용: 변경 파일 목록 + jest 결과 + tsc 결과 + git status (워킹트리만)

---

## 인수 기준

1. `prescription/index.tsx` "최근 등록" 섹션 시각적으로 사라짐 (스냅샷/UI 확인)
2. 등록 폼에서 활성 슬롯마다 시간 picker 노출, 기본값은 슬롯별 표준 시간
3. 시간 변경 → 등록 API 페이로드 `schedule.slots[].customTime`에 반영
4. 비활성 슬롯은 picker 안 나타남
5. 모든 신규 코드 jest 테스트 통과 + tsc 0 에러

## 비-범위 (out of scope)

- BE schedule.slots 처리 (이미 spec 완료, 이번에 변경 X)
- draft / finalize register 흐름 분리 — 별도 task
- 글로벌 방해금지 시간 설정 — 별도 task (설정 탭)
- TimeOfDay.BEDTIME enum 추가 (필요 시 별도 task로 분리)
