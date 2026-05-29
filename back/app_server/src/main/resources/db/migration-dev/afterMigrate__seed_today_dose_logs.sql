-- dev/staging only — 매 시작마다 오늘 날짜 dose_logs 보충 (Flyway afterMigrate 콜백)
-- afterMigrate: Flyway.migrate() 마다 항상 실행 → CURRENT_DATE 기준 idempotent INSERT

DO $$
BEGIN
    INSERT INTO dose_logs (schedule_id, patient_id, scheduled_at, status)
    SELECT s.id, 1,
           (CURRENT_DATE + CASE s.time_of_day
               WHEN 'MORNING' THEN INTERVAL '8 hours'
               WHEN 'NOON'    THEN INTERVAL '12 hours 30 minutes'
               WHEN 'EVENING' THEN INTERVAL '19 hours'
               WHEN 'BEDTIME' THEN INTERVAL '22 hours'
           END)::timestamptz,
           'PENDING'
    FROM schedules s
    WHERE s.patient_id = 1
      AND NOT EXISTS (
          SELECT 1 FROM dose_logs dl
          WHERE dl.schedule_id = s.id
            AND dl.scheduled_at::date = CURRENT_DATE
      );
END;
$$;
