# FE 복약 체크 아키텍처 Blueprint (2026-05-28 안정 버전)

> **목적**: 홈/스케줄 양방향 복약 체크 + 60초 lock + 그룹 알림 트리거가 동작하는 현재 안정 버전 의 구조·데이터흐름·핵심 파일·재발 시 진단 절차를 기록.
> **사용 시점**: 향후 동기화 오류 / RedBox / 권한 거부 발생 시 본 문서 기반으로 진단·수정.

---

## 1. 큰 그림 (한 줄)

홈/스케줄 두 화면이 **같은 `doseStateSlice` 를 Redux selector 로 구독** + **`useGetDayScheduleQuery` 단일 query** → 한 곳 변경 시 양쪽 자동 리렌더. **`useCheckDoseMutation onQueryStarted` 옵티미스틱** + **`notifyGroupTimer` middleware** 가 60초 lock + 그룹 알림 발송 자동화.

```
사용자 슬롯 클릭
    ↓
useSlotPress(공통 hook)
    ↓
[lockedAt + 60초 경과?] — YES → Alert.alert + return
    ↓ NO
checkDose mutation (PATCH /api/v1/dose-logs/check)
    ↓ onQueryStarted (옵티미스틱)
markDone dispatch (lockedAt=Date.now())
    ↓
BE 200 OK → invalidatesTags Schedule/DoseLog/Activity
    ↓ (catch 시 markWait revert + lockedAt 제거)
notifyGroupTimerMiddleware (markDone 감지)
    ↓ setTimeout(60_000, ...)
60초 경과 → notifyGroup mutation (POST /api/v1/dose-logs/:id/notify-group)
    ↓ 404/501 swallow + console.warn (BE 미구현 시)
[중간 markWait] → clearTimeout (알림 발송 취소)
```

---

## 2. 핵심 파일 구조

### 2.1 Redux state
| 파일 | 역할 |
|---|---|
| `front/src/store/slices/doseStateSlice.ts` | `Record<doseLogId, {state, lockedAt?}>` + actions `markDone(lockedAt=Date.now())` / `markWait(lockedAt 제거)` / `reset` + selector `selectSlotState(doseLogId)` / `selectIsLocked(doseLogId, now)` + 상수 `LOCK_DURATION_MS = 60_000` |
| `front/src/store/index.ts` | 모든 slice + middleware 등록 (notifyGroupTimerMiddleware 포함) |

### 2.2 RTK Query API
| 파일 | 역할 |
|---|---|
| `front/src/lib/api/client.ts` | `API_BASE_URL` 해석 (iOS=localhost:8080/api/v1, Android=10.0.2.2:8080/api/v1) |
| `front/src/lib/api/baseQuery.ts` | **`createPillmateBaseQuery()` 공통 팩토리** — Authorization + X-User-Id 헤더 단일 진입점 |
| `front/src/lib/auth/storage.ts` | `getToken()` / `getCurrentUserId()` (현재 hardcode '1', 추후 SecureStore 동적) |
| `front/src/store/slices/doseLogApi.ts` | `useCheckDoseMutation` (PATCH check + onQueryStarted optimistic + invalidatesTags + catch revert) + `useNotifyGroupMutation` (POST notify-group + 404/501 swallow) |
| `front/src/store/slices/scheduleApi.ts` | `useGetDayScheduleQuery(TODAY)` + tagTypes Schedule + providesTags + (현재) queryFn mock MOCK_SCHEDULE |
| `front/src/store/slices/activityApi.ts` | `useGetRecentActivityQuery` + transformResponse `response?.data ?? []` (**null guard 필수**) + 30초 polling |
| `front/src/store/slices/prescriptionApi.ts` | OCR + 등록 mutation + transformResponse 동일 패턴 |

### 2.3 Middleware
| 파일 | 역할 |
|---|---|
| `front/src/store/middleware/notifyGroupTimer.ts` | Redux middleware — `markDone` 감지 시 `Map<doseLogId, TimerId>` 에 `setTimeout(60_000)` 등록. `markWait` 시 `clearTimeout`. 60초 경과 시 `doseLogApi.endpoints.notifyGroup.initiate(doseLogId)` dispatch. **화면 unmount 와 무관하게 동작** (글로벌) |

### 2.4 Hook + UI
| 파일 | 역할 |
|---|---|
| `front/src/hooks/useSlotPress.ts` | **공통 hook** — `useCheckDoseMutation` + lock 체크 (`selectIsLocked`) + `Alert.alert` 차단 + action 결정 (current state 기준 toggle) |
| `front/src/app/(tabs)/home.tsx` | `useGetDayScheduleQuery(TODAY)` + selector doseStateMap overlay + `useSlotPress` 콜백 → `TimeSlotCards slots={overlaySlots}` |
| `front/src/app/(tabs)/schedule.tsx` | 같은 query + selector + useSlotPress → `MedTimeRow onPress` |
| `front/src/components/home/TimeSlotCards.tsx` | **props only** — 내부 useState 절대 X (이전 버그 원인). `displaySlots = initialSlots` 그대로 |
| `front/src/components/schedule/MedTimeRow.tsx` | `slot.state` prop 받아 렌더 + `onPress` prop |

### 2.5 테스트
| 파일 | 검증 |
|---|---|
| `front/tests/utils/renderWithStore.tsx` | Provider 헬퍼 (doseState reducer + RTK Query store) |
| `front/tests/unit/doseStateSlice.test.ts` | markDone/markWait/lockedAt/selectIsLocked |
| `front/tests/unit/useSlotPress.test.ts` | lock 전/후 + Alert mock + mutation mock |
| `front/tests/unit/baseQuery.test.ts` | createPillmateBaseQuery 가 X-User-Id 헤더 정확히 설정 |
| `front/tests/unit/notifyGroupTimer.test.ts` | setTimeout 등록 / clearTimeout / 60초 경과 시 dispatch |
| `front/tests/unit/TimeSlotCards.test.tsx` | props only 검증 (내부 state X) |
| `front/tests/unit/schedule.test.tsx` | useGetDayScheduleQuery + overlay 패턴 |

---

## 3. 데이터 흐름 시나리오

### 3.1 정상 — 사용자가 슬롯 체크 (TAKE)
```
1. 사용자 슬롯 클릭 → MedTimeRow.onPress 또는 TimeSlotCards.onSlotPress
2. useSlotPress(slot) 호출:
   - selectIsLocked(slot.doseLogId, Date.now()) check
   - lock 안 됨 → action='TAKE' 결정 (current state 'wait' 라)
   - checkDose({doseLogId: slot.doseLogId, action: 'TAKE'}) dispatch
3. onQueryStarted (옵티미스틱):
   - markDone({doseLogId, lockedAt: Date.now()}) dispatch
   - 화면 즉시 done 표시
   - 양 화면 (홈/스케줄) selector 가 같은 slice 구독이라 동시 리렌더
4. fetch PATCH /api/v1/dose-logs/check 헤더 (X-User-Id+Authorization+Content-Type)
5. BE 200 OK → invalidatesTags ['Schedule', 'DoseLog', 'Activity']
6. notifyGroupTimerMiddleware: markDone action 감지 → setTimeout(60_000)
7. 60초 동안 사용자가 다시 누르면:
   - useSlotPress: selectIsLocked = false (still 60초 안) → action='SKIP' (current 'done' 라)
   - markWait dispatch → notifyGroupTimer clearTimeout
   - BE PATCH SKIP 호출 → status SKIPPED 저장
   - 화면 wait 표시
8. 60초 경과 시:
   - notifyGroup({doseLogId}) mutation 자동 dispatch
   - POST /api/v1/dose-logs/:id/notify-group
   - BE 200 → notifications row 생성 + LogNotificationSenderAdapter [FCM-STUB] log
   - 또는 404/501 → swallow + console.warn (Phase 1 BE 미구현 시)
```

### 3.2 비정상 — BE 500 (예: X-User-Id 누락 / patient_id 불일치)
```
1. checkDose mutation → fetch
2. BE 500 → fetchBaseQuery error
3. onQueryStarted catch:
   - markWait({doseLogId}) dispatch (revert + lockedAt 제거)
   - 화면 wait 으로 즉시 revert
4. notifyGroupTimer: markWait 감지 → clearTimeout (timer 등록 안 됐어도 OK)
5. 사용자가 보기엔 "즉시 취소" — root cause 는 BE 응답 (개발 시 docker logs + psql 진단)
```

### 3.3 비정상 — RedBox `getRecentActivity Cannot read property 'data' of null`
```
원인: activityApi transformResponse 에 null guard 없음
조치: response?.data ?? [] (optional chaining) — commit 69baae9
재발 방지: 모든 transformResponse 에 동일 패턴 강제
```

---

## 4. 검증된 fix 사이클 (오늘 4단 root cause)

| # | 사용자 보고 | 진단 도구 | 진짜 원인 | 해결 commit |
|---|---|---|---|---|
| 1 | "체크 즉시 취소" | grep | TimeSlotCards 내부 useState stateMap 잔존 (props slice overlay 덮어쓰기) | `a530ba7` Fix: TimeSlotCards stateMap 제거 |
| 2 | "여전히 안 됨" | grep + docker logs | FE prepareHeaders X-User-Id 헤더 누락 | `0363879` Fix: 4 RTK slice X-User-Id hardcode (이후 `1cf6995` 공통 baseQuery 팩토리로 중앙화) |
| 3 | "여전히 안 됨" | docker exec psql | DB user_id=1 의 schedule/dose_logs 0건 (모두 user_id=2 소유) | `6e4a5cc` Feat: V19 SEED user_id=1 데이터 (이후 `8d310e4` migration-dev 분리) |
| 4 | "여전히 안 됨" | psql + grep | FE MOCK_SLOTS doseLogId 1,2,3,4 ↔ V19 SEED 실제 id 4,5,6,7 mismatch | `660902a` Fix: MOCK_SLOTS doseLogId V19 SEED 매칭 |
| 보너스 | "RedBox 떠있음" | xcrun simctl screenshot + Read | activityApi transformResponse 가 response.data ?? [] (null guard 부족) | `69baae9` Fix: transformResponse 옵셔널 체이닝 |

---

## 5. 재발 시 진단 절차 (체크리스트)

### Step 1 — 사용자 화면 확인
1. `xcrun simctl io booted screenshot /tmp/sim.png` → Read 로 확인
2. RedBox 메시지 / 빈 화면 / 잘못된 데이터 / Alert 어떤 종류인지 분류

### Step 2 — FE/BE 연결 확인
1. `docker ps` → pillmate-app healthy?
2. `docker logs --since 5m pillmate-app | grep -iE "(PATCH|POST|ERROR)"`
3. 사용자 시뮬레이터에서 슬롯 클릭 직후 docker logs 새 호출 보이는지
   - 안 보임 → FE 호출 안 됨 (Metro hot reload 안 됨 / baseUrl 잘못 / X-User-Id 누락)
   - 500 보임 → BE 측 문제 (다음 Step)

### Step 3 — BE 응답 직접 검증
```bash
docker exec pillmate-app sh -c "wget -S -O- --header='X-User-Id: 1' --header='Content-Type: application/json' --body-data='{\"doseLogId\":<id>,\"action\":\"TAKE\"}' --method=PATCH http://localhost:8080/api/v1/dose-logs/check"
```
- 200 → BE OK, FE 처리 문제
- 500 → BE 측 코드 / DB / 권한 문제 (Step 4)
- 404 → endpoint path 잘못 (context-path /api/v1 확인)

### Step 4 — DB 상태 확인
```sql
-- user_id=1 의 schedule + 오늘 dose_logs
SELECT dl.id, dl.schedule_id, s.patient_id, s.time_of_day, dl.status, dl.scheduled_at::date
FROM dose_logs dl JOIN schedules s ON s.id=dl.schedule_id
WHERE s.patient_id=1 ORDER BY dl.scheduled_at DESC LIMIT 10;

-- 오늘 4건 있어야 정상. 부족하면 R__seed_test_data_user1.sql 재실행 (docker compose down/up)
SELECT count(*) FROM dose_logs dl JOIN schedules s ON s.id=dl.schedule_id
WHERE s.patient_id=1 AND dl.scheduled_at::date=CURRENT_DATE;
```

### Step 5 — FE 코드 회귀 검증
```bash
# X-User-Id 헤더 정상?
grep -nE "X-User-Id" front/src/lib/api/baseQuery.ts

# TimeSlotCards 내부 state 가 없는지 (props only)?
grep -nE "useState|stateMap" front/src/components/home/TimeSlotCards.tsx

# transformResponse 가 옵셔널 체이닝 사용?
grep -nE "transformResponse" front/src/store/slices/*.ts

# MOCK_SLOTS doseLogId 가 현재 DB 실제 id 와 매칭?
grep -nE "doseLogId" front/src/store/slices/scheduleApi.ts
# vs
docker exec pillmate-postgres psql -U pillmate -d pillmate -c "SELECT dl.id, s.time_of_day FROM dose_logs dl JOIN schedules s ON s.id=dl.schedule_id WHERE s.patient_id=1 AND dl.scheduled_at::date=CURRENT_DATE ORDER BY s.time_of_day;"
```

### Step 6 — 시뮬레이터 reload 확인
```bash
# Metro 캐시 의심 시
cd front && npx expo start --clear
# 또는 시뮬레이터 cmd+R
```

---

## 6. 절대 회피해야 할 안티 패턴

1. **TimeSlotCards 안에 useState stateMap** — props overlay 가 무력화됨 (a530ba7 에서 제거됨)
2. **4 RTK slice prepareHeaders 안에 X-User-Id 중복 hardcode** — 4곳 동시 수정 부담 (6132de5 에서 공통 baseQuery 팩토리로 중앙화)
3. **transformResponse 가 `response.data` (옵셔널 체이닝 X)** — response null 시 RedBox (69baae9 에서 fix)
4. **V19 versioned SEED 가 1회만 실행** — 매일 dose_log 보충 안 됨 (R__ Repeatable 로 변경 — T-BE-V19-TO-REPEATABLE 진행 중)
5. **CTO 가 직접 코드 수정** — feedback_cto_no_code_edit 룰 (BE-Dev / FE-Dev 디스패치 의무)
6. **MOCK_SLOTS doseLogId hardcode vs DB 실 id mismatch** — V19/R__ SEED 결과의 schedule_id_seq 증가 시 주기적 재조정 또는 BE GET /schedules 호출로 정도 fix (T-FE-SCHEDULE-FROM-BE 후속)
7. **onQueryStarted 의 catch 가 markWait revert 만 하고 lockedAt 제거 안 함** — BE 실패 시 화면만 done + timer 유지 → 잘못된 알림 발송 (현재는 markWait 가 lockedAt 제거하니 OK)

---

## 7. 후속 정도 fix (현재는 임시)

| 임시 | 정도 | Task ID |
|---|---|---|
| `getCurrentUserId() → return 1 hardcode` | SecureStore 동적 + JWT | T-BE-USER-AUTH |
| `scheduleApi.queryFn → MOCK_SCHEDULE` | BE GET /schedules/day fetchBaseQuery query | T-FE-SCHEDULE-FROM-BE |
| `MOCK_SLOTS doseLogId hardcode` | 위 query 결과 사용 (BE 실 데이터) | 위와 동일 |
| `home.tsx MOCK_SLOTS hardcode (현재 #84 진행 중)` | scheduleApi MOCK_SCHEDULE 단일 진실 소스 | T-FE-HOME-SCHEDULE-UNIFY (#84) |
| `BE notify-group endpoint 404 swallow` | ✅ Phase 1 MVP 완료 (T-BE-NOTIFY-MVP #75) — 추후 FCM SDK | T-BE-NOTIFY-FCM |

---

## 8. 메모리 / 컨벤션 참조

- 메모리: `~/.claude/projects/-Users-user-Downloads-pillmate/memory/project_fe_dose_check_blueprint.md` (본 문서 pointer + 핵심 요약)
- 관련 메모리: `feedback_db_safety` / `feedback_context_compaction` / `feedback_cto_no_code_edit` / `feedback_qa_policy` / `researcher-reviewer-plan`
- spec 들: `.cmux/specs/T-FE-DOSE-CHECK-*` + `T-FE-XUSERID-HEADER` + `T-BE-TEST-DATA-USER1` + `T-FE-MOCK-SLOTS-ID-SYNC` + `T-FE-TRANSFORM-NULL-GUARD` + `T-BE-V19-TO-REPEATABLE` + `T-FE-HOME-SCHEDULE-UNIFY`
- 룰: `.claude/rules/common/tdd-cycle.md` + `clean-code.md` + `db-safety.md`
- 진화 로그: `docs/harness-evolution/conversation-log.md` 의 "2026-05-28 FE 동기화 4-단 root cause 정정 흐름"

---

## 9. 변경 이력

| 날짜 | 작성 | 사유 |
|---|---|---|
| 2026-05-28 | CTO | 사용자 명시 "FE 현재 버전이 좋아 이렇게 구현한 거 어떻게 했는지 기록해" — 오늘 4단 root cause + 안정 아키텍처 blueprint 기록 |
