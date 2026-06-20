-- 커스텀 복약시간 도입 — 사용자 동의 2026-06-20.
-- schedules.custom_time 은 V5 에서 정의되었으나 미사용(코드 매핑 0건) 상태였다.
-- 이를 실제 사용으로 전환: NULL 인 기존 행만 time_of_day 기본 시각으로 backfill 후 NOT NULL 제약 추가.
-- 데이터 삭제/덮어쓰기 없음 — UPDATE 는 custom_time IS NULL 행만 채운다 (db-safety 준수).
UPDATE schedules
SET custom_time = CASE time_of_day
        WHEN 'MORNING' THEN TIME '08:00'
        WHEN 'NOON'    THEN TIME '12:30'
        WHEN 'EVENING' THEN TIME '19:00'
        WHEN 'BEDTIME' THEN TIME '22:00'
    END
WHERE custom_time IS NULL;

ALTER TABLE schedules ALTER COLUMN custom_time SET NOT NULL;
