# T-HOME-AI-RECO — 홈 AI 추천 카드 (등록 트리거 + 영속 + 노출)

작성일: 2026-06-27
사용자 명시: "약봉투 등록할 때마다 AI 추천을 받고, 가장 최근에 등록된 약봉투를 기준으로 홈에 띄워주고, 어떤 약봉투에 대한 건지 명시해라"

## 배경

- BE: `back/ai_server/app/rag/health_report/service.py` + `POST /api/v1/analyze/health-report` 라우터 존재 (Gemini Flash + 출처/신뢰도 강제)
- BE: `com.pillmate.report` 컨텍스트(`health_reports`/`report_insights`) 완성 — 주간/월간 리포트 전용
- BE: `PrescriptionRegistered` 이벤트 publish (현재 NotificationDispatcher만 listen)
- FE: `front/src/components/home/InsightCard.tsx` 완성, `home.tsx:126-137` 하드코딩 mock — 실 API 연동만 필요
- 본 task는 **신규 컨텍스트 구축이 아니라**, "처방 등록 → AI 추천 생성 → 영속 → 홈 카드 노출" 파이프라인 연결

## 결정 사항 (CTO)

| 지점 | 결정 |
|------|------|
| 영속 | 신규 `prescription_insights` 테이블 (V38) — `health_reports` 재사용 X (NOT NULL 통계컬럼 부담) |
| 트리거 | `PrescriptionRegistered` @TransactionalEventListener(AFTER_COMMIT) — NotificationDispatcher 골든 패턴 |
| ai_server 라우트 | 신규 `POST /api/v1/analyze/prescription-recommendation` 분리 — period_type 의미 오염 회피 |
| 응답 경로 | `PrescriptionDetailResponse`에 insight inline + 홈용 `GET /prescriptions/latest-with-insight` 신규 |
| 보안 가드 | `PatientAccessGuard` (본인만) |
| Graceful | LLM 실패 시 insight 없이 등록 성공 — 등록 차단 X, 의료 안전 fallback |

## 절대 규칙 (재확인)

- DB 데이터 삭제 금지 (DELETE/TRUNCATE/DROP/UPDATE WHERE 없는). V38 신규 migration만, 기존 V1~V37 수정 X
- TDD 필수 (RED → GREEN → REFACTOR), 한 사이클 = 한 커밋
- DDD 레이어 의존 역전 금지 (presentation → application → domain ← infrastructure)
- 의료 안전: 추천 응답에 출처(source) + 신뢰도(confidence ≥ 0.7) 강제
- 환자 정보 로그 금지
- git commit/push는 CTO만 — 패널은 워킹트리 + DONE 보고
- clean-code: service 메서드 ≤20줄, 주석 생략, SRP, private 메서드 추출
- no-overengineering: Phase 1 단일 서버, 캐시는 prescriptionId 단위 DB 영속 자체로 충분 (별도 Redis 캐시 추가 X)

---

## BE-Dev 작업 (back/app_server + back/ai_server)

### 1. ai_server 신규 라우트 (`back/ai_server/app/api/analyze.py`)

```python
@router.post("/api/v1/analyze/prescription-recommendation", response_model=PrescriptionRecommendationResponse)
async def analyze_prescription_recommendation(
    request: PrescriptionRecommendationRequest,
    service: PrescriptionRecommendationService = Depends(get_prescription_recommendation_service),
) -> PrescriptionRecommendationResponse:
    return await service.analyze(request)
```

- 도메인: `back/ai_server/app/domain/prescription_recommendation.py`
  - `PrescriptionRecommendationRequest`: `prescription_id: int`, `patient_id: int`, `drugs: list[DrugContext]` (`code/name/dose_amount/dose_unit/frequency/duration_days`)
  - `PrescriptionRecommendationResponse`: `insights: list[InsightDraft]` (`type: WARNING|RECOMMENDATION|TREND`, `severity: INFO|WARN|CRITICAL`, `title`, `description`, `source`, `confidence`)
- 서비스: `back/ai_server/app/rag/prescription_recommendation/service.py`
  - 기존 HealthReportService 구조 차용 (`_build_user_prompt`, `_parse`, `_is_accepted`)
  - `MIN_CONFIDENCE = 0.7` 동일
  - source 비어있거나 confidence 미달 시 해당 insight drop
  - 프롬프트: 약 목록 기반 (병용주의/식이주의/복용 팁) — adherence/통계 데이터 없음
- main.py DI 와이어링 추가
- pytest 단위 테스트 추가 (fixture LLM mock, fallback 케이스 포함)

### 2. app_server `prescription` 컨텍스트 확장

#### 2.1 도메인

`back/app_server/.../prescription/domain/model/PrescriptionInsight.java` (신규 Aggregate)
- 필드: `id`, `prescriptionId`, `type`, `severity`, `title`, `description`, `source`, `confidence`, `createdAt`
- enum 클래스: `PrescriptionInsightType` (WARNING/RECOMMENDATION/TREND), `PrescriptionInsightSeverity` (INFO/WARN/CRITICAL) — report 컨텍스트 enum 복제 (Bounded Context 격리)
- 정적 팩토리: `PrescriptionInsight.create(prescriptionId, type, severity, title, description, source, confidence)` — confidence < 0.7 시 `IllegalArgumentException`
- domain test 100% (의료 도메인 강제)

#### 2.2 application

- `PrescriptionInsightRepository` 인터페이스 (domain) + JPA 구현 (infrastructure)
- 신규 port: `PrescriptionRecommendationPort` (application/port)
  - `generate(prescriptionId, patientId, List<DrugContext>) → List<InsightDraft>`
- 신규 어댑터: `infrastructure/ai/AiServerRecommendationClient` (`PrescriptionRecommendationPort` 구현)
  - 기존 `AiServerOcrClient` 패턴 차용 (공용 `AiServerClientConfig` `RestClientCustomizer`)
  - graceful: `RestClientException` → 빈 리스트 반환 + structured log (등록 흐름 차단 X)
- 신규 listener: `application/listener/PrescriptionRecommendationListener`
  - `@TransactionalEventListener(phase = AFTER_COMMIT)`
  - `on(PrescriptionRegistered event)`:
    - prescription 조회 → drug list 추출
    - `PrescriptionRecommendationPort.generate(...)` 호출 (트랜잭션 외부)
    - 결과 `PrescriptionInsight.create()` 변환 후 repository 저장
    - 전체 try-catch + log.warn (graceful)
  - NotificationDispatcher 와 같은 phase 라 `@Order(20)` 부여 (Notification 우선)
- `GetPrescriptionDetailUseCase` 수정: insight 조회 후 `PrescriptionDetailResponse`에 inline 첨부
- 신규 UseCase: `GetLatestPrescriptionWithInsightUseCase`
  - `loadLatestForPatient(userId)` — 본인 최신 처방전 1건 + insight (없으면 null)
  - `PatientAccessGuard.requireAccess(UserContext.get(), patientId)` 강제

#### 2.3 presentation

- `PrescriptionController` 신규 endpoint: `GET /prescriptions/latest-with-insight`
  - 응답 DTO: `LatestPrescriptionWithInsightResponse { prescriptionId, prescribedAt, drugCount, primaryDrugName, insights: [...] }`
  - 라벨 메타: FE에서 `prescribedAt` + `drugCount` 조합으로 "○월○일 등록 약봉투 (약 N개) 기준" 표시
- `PrescriptionDetailResponse`에 `insights: List<PrescriptionInsightView>` 필드 추가 (nullable)

#### 2.4 DB 마이그레이션

`back/app_server/src/main/resources/db/migration/V38__create_prescription_insights.sql`
```sql
CREATE TABLE prescription_insights (
    id BIGSERIAL PRIMARY KEY,
    prescription_id BIGINT NOT NULL REFERENCES prescriptions(id),
    type VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    source VARCHAR(200) NOT NULL,
    confidence NUMERIC(4, 3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_prescription_insights_type CHECK (type IN ('WARNING','RECOMMENDATION','TREND')),
    CONSTRAINT chk_prescription_insights_severity CHECK (severity IN ('INFO','WARN','CRITICAL')),
    CONSTRAINT chk_prescription_insights_confidence CHECK (confidence >= 0.700)
);
CREATE INDEX idx_prescription_insights_prescription ON prescription_insights(prescription_id);
CREATE INDEX idx_prescription_insights_created_at ON prescription_insights(created_at DESC);
```

### 3. 테스트 (TDD 필수)

- domain: `PrescriptionInsightTest` (confidence < 0.7 reject, factory)
- application:
  - `PrescriptionRecommendationListenerTest` (port mock, repository 저장 검증, graceful catch 검증)
  - `GetPrescriptionDetailUseCaseTest` (insight inline 포함 검증)
  - `GetLatestPrescriptionWithInsightUseCaseTest` (PatientAccessGuard, insight 없음 케이스)
- presentation: `PrescriptionControllerTest` (@WebMvcTest)
  - `GET /prescriptions/latest-with-insight` 응답 스키마
  - `GET /prescriptions/{id}` 응답에 insights 필드 포함
- infrastructure:
  - `AiServerRecommendationClientTest` (MockRestServiceServer) — 200 응답 파싱 / 5xx graceful
- ArchUnit: prescription 컨텍스트 격리 유지 검증

### 4. 보고 형식

작업 완료 시:
- 파일: `.cmux/messages/cto/inbox/T-HOME-AI-RECO-be-done.json`
- 내용: 변경 파일 목록 + 테스트 결과 + 빌드 결과 + git status (커밋 X, 워킹트리만)

---

## FE-Dev 작업 (front/)

### 1. RTK Query 슬라이스

`front/src/store/slices/prescriptionApi.ts` 확장
- 신규 endpoint: `getLatestWithInsight` (query)
  - `query: () => '/prescriptions/latest-with-insight'`
  - `providesTags: ['Prescription']`
  - 응답 타입: `LatestPrescriptionWithInsight { prescriptionId, prescribedAt, drugCount, primaryDrugName, insights: PrescriptionInsight[] }`
- 기존 `getDetail` 응답에 `insights` 필드 동기화

`front/src/types/prescription.ts`
- `PrescriptionInsight { id, type, severity, title, description, source, confidence }` 타입 추가

### 2. 홈 카드 연동

`front/src/app/(tabs)/home.tsx`
- L126~137 하드코딩 mock 제거
- `useGetLatestWithInsightQuery()` 사용
- insight 없음 / API 실패 → 카드 숨김
- insight 있음 → `InsightCard`에 첫 번째 insight 표시 + 라벨 메타 prop 추가

`front/src/components/home/InsightCard.tsx`
- 신규 prop: `subtitle?: string` (예: "9월 12일 등록 약봉투 (약 3개) 기준")
- `styles.detail` 위에 `<Text style={styles.subtitle}>{subtitle}</Text>` 한 줄 렌더 (조건부)
- 디자인 토큰: `typography.caption1`, `colors.labelAlternative`
- `onDetail` → `/prescription/[id]` (prescriptionId 라우팅 — 기존 `/report` 라우팅 대신)

`front/src/utils/calendarUtils.ts` (또는 동등 위치)
- `formatMonthDay(date)` 활용 ("○월○일 등록 약봉투 (약 N개) 기준" 빌드 헬퍼)

### 3. 처방전 상세에 insight 표시 (선택 — 기본 포함 권장)

`front/src/app/prescription/[id].tsx`
- 응답에 `insights` 있으면 detail 화면 상단/하단에 카드 형태 노출 (홈 카드 컴포넌트 재사용)

### 4. 테스트

- `front/tests/unit/InsightCard.test.tsx` — subtitle prop 렌더 검증
- `front/tests/unit/home.test.tsx` (또는 생성) — insight 없음/있음 분기

### 5. 보고 형식

작업 완료 시:
- 파일: `.cmux/messages/cto/inbox/T-HOME-AI-RECO-fe-done.json`
- 내용: 변경 파일 목록 + jest 결과 + tsc 결과 + git status

---

## 상호 의존성

- BE가 신규 endpoint(`GET /prescriptions/latest-with-insight`) + 응답 스키마 확정 → FE는 그 응답 타입 차용
- 병렬 가능: FE는 BE 응답 타입을 본 spec 그대로 가정하고 mock 데이터로 진행 가능
- BE PR이 먼저 머지되면 FE는 mock 제거 단계만 남음

## 인수 기준

1. 처방전 등록 → 비동기 listener가 ai_server 호출 → `prescription_insights` 저장 (DB 직접 확인)
2. `GET /prescriptions/latest-with-insight` 응답에 최신 처방전 + insight 포함 (또는 insight 없음 시 nullable)
3. 홈 진입 시 `InsightCard`가 BE 데이터로 노출 + "○월○일 등록 약봉투 (약 N개) 기준" 라벨 표시
4. ai_server 다운 시 등록 정상 성공 + insight 없는 상태로 홈/상세 표시 (등록 차단 X)
5. 본인 외 처방전 latest-with-insight 호출 시 403
6. 모든 신규 코드 TDD (RED → GREEN), domain 100% 커버리지

## 비-범위 (out of scope)

- 주간/월간 리포트(`com.pillmate.report` 컨텍스트)는 본 task에서 건드리지 않음
- prescription_insights 캐시 (Redis) — Phase 2 검토
- insight 재생성 트리거 (수정 시) — Phase 2
- 다중 insight 페이지네이션 — 첫 1건만 표시
