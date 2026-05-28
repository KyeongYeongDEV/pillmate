-- dev/staging only — 매 시작마다 user_id=1 의 오늘 dose_logs 보충 (멱등)
-- R__ (Repeatable): 체크섬 변경 시 재실행. NOT EXISTS 로 중복 삽입 방지.

DO $$
DECLARE
    v_group_id BIGINT;
BEGIN
    -- 1. user_id=1 가 그룹에 없으면 테스트 그룹 + 멤버십 생성
    IF NOT EXISTS (SELECT 1 FROM memberships WHERE user_id = 1) THEN
        INSERT INTO care_groups (name, created_by, created_at, updated_at)
        VALUES ('테스트그룹(dev)', 1, NOW(), NOW())
        RETURNING id INTO v_group_id;

        INSERT INTO memberships (care_group_id, user_id, role, joined_at)
        VALUES (v_group_id, 1, 'PATIENT', NOW());
    ELSE
        SELECT care_group_id INTO v_group_id
        FROM memberships WHERE user_id = 1
        LIMIT 1;
    END IF;

    -- 2. user_id=1 용 4슬롯 schedule (없는 time_of_day 만 삽입)
    INSERT INTO schedules (care_group_id, patient_id, drug_id, time_of_day, start_date, end_date, active, created_by, created_at)
    SELECT v_group_id, 1, d.id, slot.time_of_day,
           CURRENT_DATE, CURRENT_DATE + INTERVAL '365 days', true, 1, NOW()
    FROM (VALUES ('MORNING'::varchar), ('NOON'::varchar), ('EVENING'::varchar), ('BEDTIME'::varchar)) AS slot(time_of_day)
    CROSS JOIN LATERAL (SELECT id FROM drugs ORDER BY id LIMIT 1) d
    WHERE NOT EXISTS (
        SELECT 1 FROM schedules
        WHERE patient_id = 1 AND time_of_day = slot.time_of_day
    );

    -- 3. 오늘 날짜 dose_logs PENDING 보충 (각 schedule 별로 오늘 row 없으면 삽입)
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
