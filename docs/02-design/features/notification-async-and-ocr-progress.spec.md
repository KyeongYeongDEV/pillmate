# T-NOTIFY-ASYNC + T-OCR-PROGRESS-UI — cold register 1초 + OCR 체감 지연 UX

작성일: 2026-06-28
사용자 선택: 1+2 진행 (3 dangling 처방 / 4 추가 UI는 보류)

---

## A. T-NOTIFY-ASYNC (BE-Dev)

### 진단
`PrescriptionRegistered` 이벤트 listener 2종이 같은 thread 동기 실행:
- `PrescriptionRecommendationListener` — 이미 `@Async("insightTaskExecutor")` 적용 (T-PERCEIVED-LATENCY-FIX 완료)
- `NotificationDispatcher` — **여전히 동기 실행** (FCM 발송 + DB 알림 저장이 register 응답 thread에서 진행)

→ register API **cold 응답 2.4초** 잔여 = NotificationDispatcher 영향 (T-PERCEIVED-LATENCY 보고서 명시).

### 작업

#### 1. NotificationDispatcher 비동기화
파일: `back/app_server/.../notification/application/listener/NotificationDispatcher.java` (또는 동등 위치)
- 각 `on(...)` 메서드(`PrescriptionRegistered`, `DdiCriticalDetected`, `WeeklyReportGenerated` 등)에 `@Async("insightTaskExecutor")` 또는 별도 executor 추가
- 결정: **insightTaskExecutor 재사용** (core=2, max=8, queue=50 — 알림 + 추천 둘 다 외부 I/O이라 같은 pool 적합. 단, 알림 우선순위 명확 분리 필요 시 신규 `notificationTaskExecutor` 추가 — 본 task 범위 외)
- 기존 `@TransactionalEventListener(AFTER_COMMIT)` 유지 (트랜잭션 commit 후 + async 실행)
- 기존 try-catch graceful 유지

#### 2. 테스트
- `NotificationDispatcherTest` 갱신: 기존 sync 동작 검증 그대로 (단위테스트는 직접 호출이라 @Async 무영향)
- (선택) 통합 테스트: register API cold 응답 시간 ≤ 1초

### 인수
1. NotificationDispatcher 메서드에 `@Async` 적용 (모든 listener)
2. register API cold 응답 ≤ 1초 (warm은 이미 0.66s)
3. FCM 발송 + 알림 DB 저장은 background 진행 (기능 무변경)
4. ./gradlew test PASS, ArchUnit 통과

### 보고
`.cmux/messages/cto/inbox/T-NOTIFY-ASYNC-be-done.json`
포함: 변경 파일 + 테스트 + cold register 시간 측정 + git status

---

## B. T-OCR-PROGRESS-UI (FE-Dev)

### 진단
OCR 호출 5~60초 (Gemini quota 정상 시 5-10초, retry 시 30-60초). 현재 UX:
- "약 인식 중…" 단순 텍스트
- 30s 후 "오래 걸리네요…"
- 60s 후 "다시 시도" 활성화

→ 사용자가 진행 상황 모름. progress bar 또는 단계 표시 없으면 체감 시간 ↑.

### 작업

#### 1. 진행 표시 컴포넌트
파일: `front/src/components/prescription/OcrProgress.tsx` (NEW)
- 시간 베이스 가짜 progress (60s 기준 0%~95%, 끝나면 100% 또는 stop)
- 단계 표시:
  ```
  [✓] 이미지 업로드           (0~3s)
  [⟳] AI 약 인식 중…           (3~30s, 활성)
  [ ] 약 정보 매칭            (대기)
  ```
- 또는 단순 progress bar + 시간 + 진행 메시지
- 30s 초과 시 메시지 변경 ("오래 걸리네요, 잠시만 더…")
- 60s 초과 시 "다시 시도" 버튼 노출

#### 2. camera.tsx + scan.tsx 통합
- 기존 로딩 화면 자리에 `<OcrProgress />` 컴포넌트 사용
- 시작 시각 state (이미 있을 듯) 그대로 전달
- 단계별 hook: `useOcrProgress(startTime)` 또는 컴포넌트 내부 setInterval

#### 3. 디자인
- 디자인 토큰 활용 (`colors.primaryNormal`, `colors.bgAlt`, `typography.body1n`, `radius.r12`)
- 부드러운 애니메이션 (LayoutAnimation 또는 단순 width 변화)
- 시각적 단계 활성/완료 표현 (체크/스피너/대기)
- 다크모드 자동 적용

#### 4. 테스트
- `front/tests/unit/OcrProgress.test.tsx` (NEW) — 단계 진행/메시지 변경/60s 후 버튼 노출
- jest fake timers

### 인수
1. OCR 호출 동안 시간 베이스 progress 표시
2. 단계 표시 (업로드 / AI 분석 / 매칭) 또는 동등 시각 표현
3. 30s/60s 임계 메시지 + button 분기 (기존 UX 유지)
4. jest + tsc 0

### 보고
`.cmux/messages/cto/inbox/T-OCR-PROGRESS-UI-fe-done.json`
포함: 변경 파일 + jest/tsc + 컴포넌트 디자인 설명 + git status

---

## 규칙 (양 task 공통)

- TDD: 신규 컴포넌트/listener 테스트
- DDD 의존 역전 X
- git commit/push 금지 (CTO 단독)
- clean-code: SRP, 매직넘버 상수화 (PROGRESS_MAX_SECONDS, SLOW_THRESHOLD_MS 등)
- no-overengineering: 신규 라이브러리 X, 기존 디자인 토큰 활용
- medical-safety/db-safety 무관 (UX + 비동기화만)

## 비-범위

- 실제 BE→FE 진행 상황 push (SSE/WebSocket) — Phase 2~3
- NotificationDispatcher 별도 executor 분리 — 본 task는 insightTaskExecutor 재사용. 분리 필요성 운영 측정 후 결정
- OCR job 비동기 polling (구조 변경) — 별도 spec
