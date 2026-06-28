# T-HOME-SCHEDULE-TIME-MERGE — 홈 복약 슬롯을 시간 단위로 합치고 처방전 이름들 표시

작성일: 2026-06-28
사용자 명시: "홈화면에서 알약별로 다 복약을 띄우는 게 아니라 그냥 시간 별로 띄우고 복약 이름을 해당 시간에 등록되어 있는 처방전 이름만 작성"

## 진단

현재 `GetDayScheduleService.mergeToSlots`:
```java
String key = slotId(formatTime(row.customTime()), row.prescriptionId(), row.scheduleId());
```
→ 시간 + 처방전 단위 그룹화 → 같은 시간 다른 처방전이면 별개 슬롯 → 홈에 여러 줄 표시.

사용자 요구: 시간 단위만 그룹화 + 슬롯 라벨에 그 시간의 처방전 이름들.

## CTO 결정

- **A1**: 처방전 이름 콤마 구분 표시 (`e2e점검약, 스포트라정약봉투`)
- **B1**: 1건 → 상세 / 여러건 → 첫 처방전 상세 (단순)
- **C1**: 시간 단위 일괄 복약 체크 (모든 약봉투 doseLog 한꺼번에)

## 절대 규칙

- TDD: `GetDayScheduleServiceTest` 시간 머지 회귀
- DDD 의존 역전 X
- DB-safety: SELECT만, 마이그레이션 0
- medical-safety: 출처/체크 무결성 보존
- git commit/push 금지 (CTO 단독)
- clean-code: SRP, slotId 시그니처 단순화
- no-overengineering: 신규 컴포넌트 X, 기존 SlotView 확장

---

## BE-Dev 작업

### 1. `GetDayScheduleService.mergeToSlots` 그룹화 변경
파일: `back/app_server/src/main/java/com/pillmate/schedule/application/GetDayScheduleService.java`

- 그룹 키: `slotId(time, prescriptionId, scheduleId)` → **`time`만**
- 같은 시간 모든 row 합치기
- `prescriptionName` 빌드 로직 변경:
  - row 들에서 처방전 라벨 unique 추출 (prescriptionId null → singleDrugName fallback)
  - 콤마(`, `) 구분 join — 최대 5건 후 `외 N건` ellipsis (UI 안전망)
- `prescriptionId` 필드: 첫 처방전 ID (B1 — 단일 처방전이면 그 ID, 여러건이면 첫 번째 — FE 탭 시 상세 이동 정합)
- 신규 필드 후보: `prescriptionIds: List<Long>` (FE 탭 시 분기 선택 가능성 대비, 선택적 — 본 변경에서 단순 X로 미추가)

### 2. `SlotView` DTO 검토
파일: `back/app_server/.../schedule/application/dto/SlotView.java`
- 기존 `prescriptionName` String 그대로 사용 (콤마 join 값)
- 신규 필드 추가 X — 단순 호환 유지 (FE 무변경)
- `doseLogIds` 는 이미 List<Long> → 시간 단위 머지 후 모든 ID 자연스럽게 합쳐짐
- `drugCount` = 합쳐진 모든 약 수
- `items` = 모든 약 이름 (정보용)

### 3. 라벨 빌드 헬퍼
- `joinPrescriptionLabels(List<String> labels)` private 메서드 추가 (clean-code SRP)
- 입력: 각 row 의 prescriptionName 또는 singleDrugName
- 출력: `"라벨1, 라벨2"` 또는 `"라벨1, 라벨2, 라벨3 외 2건"` (5건 초과 시)

### 4. 테스트
- `GetDayScheduleServiceTest` 갱신:
  - 같은 시간 다른 처방전 2건 → 슬롯 1개, prescriptionName = 콤마 join
  - 같은 시간 5건 초과 → "외 N건" ellipsis
  - 모든 약 done → state="done", 미완 1건 있으면 "wait"
  - doseLogIds 모두 포함
  - drugCount 합산
  - 단일 처방전(레거시 schedule) 무변경

### 5. 인수
1. 같은 시간 다른 처방전 → 슬롯 1개로 머지
2. 라벨에 처방전 이름들 콤마 구분 (`A, B`)
3. 5건 초과 시 `A, B, C 외 N건`
4. doseLogIds 시간 단위로 합쳐져 일괄 체크 가능 (FE 기존 동작 그대로 작동)
5. 단일 처방전 케이스 무회귀
6. ./gradlew test PASS, ArchUnit 통과

### 6. 보고
`.cmux/messages/cto/inbox/T-HOME-SCHEDULE-TIME-MERGE-be-done.json`
포함: 변경 파일 + 테스트 결과 + e2e (GET /schedules/days/{date}) 응답 sample + git status

---

## FE-Dev 작업 (선택, 가벼움)

### 1. TimeSlotCards.tsx
- 무변경 (BE 응답 그대로 받아서 표시)
- 만약 콤마 join 라벨이 카드 너비 넘으면 `numberOfLines={2}` + `ellipsizeMode="tail"` 적용 검토
- 현재 `prescriptionName` 표시 위치 확인 후 길이 trade-off 결정

### 2. 테스트
- 변경 없으면 스킵
- 변경 시 스냅샷/시각 회귀

### 3. 보고
필요 시 `.cmux/messages/cto/inbox/T-HOME-SCHEDULE-TIME-MERGE-fe-done.json`
변경 없으면 BE 머지 후 시뮬레이터 확인으로 충분

---

## 비-범위

- 슬롯 펼치기/접기 UI (약봉투별 분리 표시) — 별도 spec
- 같은 시간 다 체크 vs 약봉투별 체크 분리 — C1 일괄 채택, C2 보류
- 슬롯 라벨에 처방전 ID 메타 노출 — UI 결정 후 별도
