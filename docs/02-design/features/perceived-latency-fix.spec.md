# T-PERCEIVED-LATENCY-FIX — OCR/등록/추천 체감 지연 일괄 해소

작성일: 2026-06-27
사용자 명시:
- "약 인식에 시간이 오래 걸려요" 화면 빈번 (OCR 지연)
- "약봉투 등록하기를 누르면 로딩이 돌면서 지연" → 비동기로 약봉투 먼저 등록되고 AI 답변 오면 상세에 추가하는 방식 원함

## 진단 (CTO)

| 단계 | 현재 동작 | 문제 |
|------|----------|------|
| 1. OCR | `AiServerOcrClient` read timeout 30초. Gemini 503 retry 발생 시 47초 처리 → 30초 cutoff | "약 인식 시간 오래" 화면 빈번 |
| 2. 등록 | `RegisterPrescriptionService` 트랜잭션 commit 후 `PrescriptionRegistered` 이벤트 publish | OK |
| 3. AI 추천 listener | `@TransactionalEventListener(AFTER_COMMIT)` **동기 실행** (같은 thread) — `@Async` 없음 | register API 응답이 AI 호출 끝날 때까지 대기 (47초) |
| 4. FE 상세 | `useGetPrescriptionDetail(id)` 1회 query, insight 없으면 그대로 빈 상태 | listener 비동기화 후 insight 도착해도 화면 갱신 없음 |

## 절대 규칙 (재확인)

- TDD 필수 (BE 비동기 listener 테스트)
- DDD 레이어 의존 역전 X
- 의료 안전 graceful: AI 실패 시 등록 정상 + insight 없음
- DB-safety: DELETE/TRUNCATE/DROP 0, V39 신규 migration 불필요 (스키마 변경 X)
- git commit/push 금지 (CTO 단독)
- clean-code: service ≤20줄, SRP
- no-overengineering: 표준 `@Async` + ThreadPoolTaskExecutor, FE polling은 RTK Query 표준 옵션

---

## BE-Dev 작업 (3 영역)

### A. OCR timeout 30→60초 + 명확 에러 메시지

#### A.1 환경/설정
- `back/app_server/src/main/resources/application.yml` (또는 환경별 profile):
  ```yaml
  ai-server:
    timeout:
      connect: 5s
      read: 60s   # 30s → 60s
  ```
- 또는 `AiServerClientConfig` 에 직접 명시된 30s 상수가 있으면 60s 로

#### A.2 AiServerOcrClient 에러 메시지 명확화
- `ResourceAccessException` 또는 timeout 류 → `PillmateException(ErrorCode.OCR_UPSTREAM_TIMEOUT)` (기존 ErrorCode 있는지 확인 — 없으면 추가)
- `RestClientException` 응답 본문 파싱 실패 → 별도 OCR_UPSTREAM_INVALID_RESPONSE
- FE 가 받는 메시지가 "약 인식 시간이 오래 걸려요" 분기 적용되도록 ErrorCode → HTTP status 매핑 유지

#### A.3 테스트
- `AiServerOcrClientTest` 신규 케이스: SocketTimeout 시뮬레이션 → `PillmateException(OCR_UPSTREAM_TIMEOUT)` 검증

### B. AI 추천 listener 비동기화 (체감 지연 핵심)

#### B.1 @Async + @EnableAsync
- 메인 설정 클래스 (`PillmateApplication` 또는 `AsyncConfig` 신규)에 `@EnableAsync`
- 신규 `AsyncConfig`:
  ```java
  @Configuration
  @EnableAsync
  public class AsyncConfig {
      @Bean(name = "insightTaskExecutor")
      public Executor insightTaskExecutor() {
          ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
          exec.setCorePoolSize(2);
          exec.setMaxPoolSize(8);
          exec.setQueueCapacity(50);
          exec.setThreadNamePrefix("insight-");
          exec.initialize();
          return exec;
      }
  }
  ```

#### B.2 PrescriptionRecommendationListener
- 메서드에 `@Async("insightTaskExecutor")` 추가
- `@TransactionalEventListener(phase = AFTER_COMMIT)` 유지 (트랜잭션 commit 후 + async 실행)
- 기존 try-catch graceful 유지

#### B.3 테스트
- `PrescriptionRecommendationListenerTest` 갱신: async 동작 검증 (mock executor 또는 동기 모드로 검증 + production 설정만 async)
- 통합 테스트 (선택): register API 응답 시간 ≤ 1초 (AI 호출 무관)

### C. (포함) PrescriptionRegistered listener 의 NotificationDispatcher 영향
- NotificationDispatcher 도 동일 phase = AFTER_COMMIT 동기 → 알림 발송이 register 응답 지연시킬 수 있음. 이번 작업에 같은 `@Async` 적용 검토 (FCM 호출도 외부 I/O)
- 또는 일관성 위해 모든 AFTER_COMMIT listener 에 `@Async` 적용 — 별도 task 분리도 OK (CTO 판단)
- **본 task 범위: PrescriptionRecommendationListener 만 async 적용. NotificationDispatcher 는 별도 검토.**

### D. 인수 기준 (BE)
1. ai-server read timeout 60s 확인 (yml + 또는 client config)
2. OCR 시 Gemini 503 시뮬레이션 → 47초 처리 가능, FE 가 OCR_UPSTREAM_TIMEOUT 명확 받음 (60초 초과 시)
3. POST /prescriptions register 응답 시간 ≤ 1초 (AI 호출 무관, time 측정)
4. AI 호출은 background 진행 → 1~3초 후 prescription_insights row 영속
5. ai_server 다운 시 register 정상 + insight 없음 (graceful 유지)
6. 모든 신규 코드 TDD, ArchUnit 통과

### E. 보고
`.cmux/messages/cto/inbox/T-PERCEIVED-LATENCY-FIX-be-done.json`
포함: 변경 파일 + 테스트 + register API 시간 측정 + e2e 호출 결과

---

## FE-Dev 작업

### F. prescription 상세 화면 polling (insight 도착 자동 갱신)

#### F.1 prescriptionApi.ts
- `getPrescriptionDetail` query 에 `refetchOnMountOrArgChange: true` 유지
- 별도 `useGetPrescriptionDetailWithPolling` 커스텀 hook 또는 컴포넌트 측에서:
  ```typescript
  const { data, refetch } = useGetPrescriptionDetailQuery(id);
  
  useEffect(() => {
    if (!data) return;
    if (data.insights && data.insights.length > 0) return;
    
    let attempts = 0;
    const MAX = 3;
    const INTERVAL = 3000;
    const timer = setInterval(() => {
      if (attempts >= MAX) { clearInterval(timer); return; }
      refetch();
      attempts++;
    }, INTERVAL);
    return () => clearInterval(timer);
  }, [data, refetch]);
  ```
- 또는 RTK Query 의 `pollingInterval` 옵션 활용 + 도착 시 stop (skip)

#### F.2 prescription/[id].tsx
- 위 hook 적용 — insights 없으면 3초 간격 최대 3회 (총 9초) refetch
- 단순 placeholder UI: "AI 인사이트 생성 중…" (3초 간격에 1회만 표시, 도착 시 카드 노출)
- 도착 시 InsightCard 자연스럽게 fade-in (애니메이션 X — 단순 노출)

#### F.3 사용자 등록 직후 → 상세 진입 흐름
- review.tsx 등록 성공 후 prescription/{id} 라우팅 시점에 polling 시작
- 약 3-9초 내 insight 노출되는 UX

### G. 테스트
- `front/tests/unit/prescriptionDetail.test.tsx` 또는 hook 단위 테스트 — polling 동작, 도착 시 stop, MAX 도달 시 stop

### H. 인수 기준 (FE)
1. 등록 직후 상세 진입 → "AI 인사이트 생성 중…" placeholder 표시 (또는 빈 상태)
2. 3초 간격 refetch, insight 도착 시 InsightCard 자동 노출
3. 9초 (3회 시도) 후에도 없으면 placeholder 사라짐 (또는 "추천 없음" 빈 상태)
4. tsc 0, jest 통과

### I. 보고
`.cmux/messages/cto/inbox/T-PERCEIVED-LATENCY-FIX-fe-done.json`
포함: 변경 파일 + jest/tsc + git status

---

## 비-범위

- WebSocket/SSE 실시간 push — Phase 4
- FCM 알림 push 도착 시 상세 화면 즉시 갱신 — 별도 task
- NotificationDispatcher `@Async` 적용 — 본 task 후 별도 검토
- BGE reranker XLMRobertaTokenizer 호환 fix — 별도 task (graceful fallback 중)
