CREATE TABLE schedules (
    id              BIGSERIAL PRIMARY KEY,
    care_group_id   BIGINT NOT NULL,
    patient_id      BIGINT NOT NULL,
    drug_id         BIGINT NOT NULL REFERENCES drugs(id),
    prescription_id BIGINT,
    time_of_day     VARCHAR(20) NOT NULL CHECK (
                        time_of_day IN ('MORNING','NOON','EVENING','BEDTIME')),
    custom_time     TIME,
    start_date      DATE NOT NULL,
    end_date        DATE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by      BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_schedules_patient ON schedules (patient_id, active);
CREATE INDEX idx_schedules_group   ON schedules (care_group_id);

CREATE TABLE dose_logs (
    id           BIGSERIAL,
    schedule_id  BIGINT NOT NULL,
    patient_id   BIGINT NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','TAKEN','SKIPPED',
                                       'DELAYED','MISSED')),
    checked_at   TIMESTAMPTZ,
    checked_by   BIGINT,
    skip_reason  TEXT,
    PRIMARY KEY (id, scheduled_at)
) PARTITION BY RANGE (scheduled_at);

CREATE TABLE dose_logs_2026_05 PARTITION OF dose_logs
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE dose_logs_2026_06 PARTITION OF dose_logs
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE dose_logs_2026_07 PARTITION OF dose_logs
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE INDEX idx_dose_logs_patient_time
    ON dose_logs (patient_id, scheduled_at DESC);
CREATE INDEX idx_dose_logs_schedule
    ON dose_logs (schedule_id, status);
CREATE INDEX idx_dose_logs_pending
    ON dose_logs (scheduled_at) WHERE status = 'PENDING';
