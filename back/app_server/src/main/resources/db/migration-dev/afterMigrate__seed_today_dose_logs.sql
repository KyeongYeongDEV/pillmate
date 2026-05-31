-- dev/staging only — 매 시작마다 오늘 (KST) 날짜 dose_logs 보충 (Flyway afterMigrate 콜백)
-- afterMigrate: Flyway.migrate() 마다 항상 실행 → KST 기준 today idempotent INSERT
-- KST 사용 이유: DB timezone=UTC 환경에서 KST 새벽 재시작 시 어제 dose_log 생성 방지

DO $$
DECLARE
    kst_today DATE := (NOW() AT TIME ZONE 'Asia/Seoul')::date;
BEGIN
    INSERT INTO dose_logs (schedule_id, patient_id, scheduled_at, status)
    SELECT s.id, 1,
           ((kst_today + CASE s.time_of_day
               WHEN 'MORNING' THEN INTERVAL '8 hours'
               WHEN 'NOON'    THEN INTERVAL '12 hours 30 minutes'
               WHEN 'EVENING' THEN INTERVAL '19 hours'
               WHEN 'BEDTIME' THEN INTERVAL '22 hours'
           END) AT TIME ZONE 'Asia/Seoul'),
           'PENDING'
    FROM schedules s
    WHERE s.patient_id = 1
      AND NOT EXISTS (
          SELECT 1 FROM dose_logs dl
          WHERE dl.schedule_id = s.id
            AND (dl.scheduled_at AT TIME ZONE 'Asia/Seoul')::date = kst_today
      );
END;
$$;
