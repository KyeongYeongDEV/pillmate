-- activity_feeds 의 enum 3컬럼에 형제 테이블과 동일한 DB 가드(CHECK/NOT NULL) 추가.
-- 배경: 이 3컬럼은 전 테이블 @Enumerated 컬럼 중 유일하게 CHECK 가 없어, enum 밖 문자열이
-- 1행이라도 들어가면 Hibernate EnumJavaType.fromName() 예외가 리스트 결과 전체로 전파돼
-- GET /groups/{id}(상세) + 그룹목록 두 최다호출 엔드포인트가 통째로 500 이 된다.
-- 사전 확인(2026-09-11): activity_type/severity/time_slot 위반 데이터 0건, severity NULL 0건.

-- severity: NULL 백필 후 NOT NULL 승격 (앱 create 팩토리는 이미 null→INFO 강제, 방어적 backfill)
UPDATE activity_feeds SET severity = 'INFO' WHERE severity IS NULL;

ALTER TABLE activity_feeds ALTER COLUMN severity SET DEFAULT 'INFO';
ALTER TABLE activity_feeds ALTER COLUMN severity SET NOT NULL;

ALTER TABLE activity_feeds
    ADD CONSTRAINT chk_activity_feeds_activity_type
    CHECK (activity_type IN ('DOSE_TAKEN', 'DOSE_MISSED', 'DOSE_CANCELED', 'NUDGE_SENT'));

ALTER TABLE activity_feeds
    ADD CONSTRAINT chk_activity_feeds_severity
    CHECK (severity IN ('INFO', 'WARN'));

-- time_slot 은 nudge 등 시간대 무관 활동에서 NULL 이 정상 → NULL 허용 + 값이 있으면 enum 내
ALTER TABLE activity_feeds
    ADD CONSTRAINT chk_activity_feeds_time_slot
    CHECK (time_slot IS NULL OR time_slot IN ('MORNING', 'NOON', 'EVENING', 'BEDTIME'));
