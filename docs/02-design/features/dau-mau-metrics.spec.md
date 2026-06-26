# Spec — DAU/MAU 서버측 집계 + Prometheus 지표

> [메모리 monitoring 플랜] 서버측 자체 집계. Grafana에서 `pillmate_dau`/`pillmate_mau` 그래프.
> 인증(#99) 됐으니 사용자별 활동 추적 가능. ★개인정보·건강정보 저장 X (user_id+날짜만).

## 데이터 모델 (V34, additive)
- `user_daily_activity(id BIGSERIAL PK, user_id BIGINT NOT NULL, active_date DATE NOT NULL, created_at TIMESTAMPTZ default now())`
- `UNIQUE(user_id, active_date)` (하루 1행), `INDEX(active_date)`
- ★DROP/DELETE 없음. (오래된 행 정리는 후속, 지금은 누적)

## 활동 기록
- 인증된 요청(UserContext present)에서 ★best-effort upsert: `INSERT ... (user_id, today) ON CONFLICT (user_id, active_date) DO NOTHING`.
- 위치: 가벼운 컴포넌트(인터셉터 afterCompletion 또는 별도) — 본업 막지 말 것(실패 무시).
- unique 제약으로 사용자당 하루 1 insert만(스로틀 자동). 트랜잭션 내 외부호출 X.
- /actuator/**, /auth/** 등 비인증 경로는 기록 안 함.

## 지표 (micrometer Gauge → /actuator/prometheus)
- `pillmate_dau`: COUNT(DISTINCT user_id) WHERE active_date = 오늘
- `pillmate_mau`: COUNT(DISTINCT user_id) WHERE active_date >= 오늘-29 (30일)
- ★@Scheduled(예: 5분 주기)로 쿼리해서 Gauge 값 갱신(매 요청 쿼리 금지). 기존 monitoring 메트릭 설정에 등록.
- 저-cardinality(라벨에 user_id 금지).

## (선택) /admin/stats
- GET /api/v1/admin/stats → {dau, mau} (보호 — 일단 인증 필요, 추후 admin 역할). Grafana엔 메트릭이 주, 이건 옵션.

## 검증
- 활동 upsert 후 DAU≥1, 같은 사용자 같은 날 중복 insert 안 됨, 지표 Gauge 노출(/actuator/prometheus에 pillmate_dau/mau). 
- TDD: 집계 쿼리(오늘/30일 distinct), 인증요청 기록·비인증 미기록, Gauge 갱신.

## 안전
- 환자/건강정보 저장 금지(user_id+date만), db-safety(삭제 없음), clean-code. 기존 V1~V33 수정 금지(신규 V34).
