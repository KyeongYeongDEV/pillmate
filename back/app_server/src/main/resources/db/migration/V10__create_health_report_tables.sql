-- T010: 건강 리포트 3-Layer 아키텍처
-- Layer A 실시간(SQL), Layer B rule-based 통계(cron), Layer C LLM 인사이트(주1회+이벤트)
CREATE TABLE health_reports (
    id              BIGSERIAL PRIMARY KEY,
    care_group_id   BIGINT NOT NULL,
    patient_id      BIGINT NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    period_type     VARCHAR(10) NOT NULL
                        CHECK (period_type IN ('WEEKLY','MONTHLY')),
    overall_score   INTEGER NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
    score_delta     INTEGER,
    adherence_rate  NUMERIC(5,2) NOT NULL,
    total_doses     INTEGER NOT NULL,
    taken_doses     INTEGER NOT NULL,
    skipped_doses   INTEGER NOT NULL,
    delayed_doses   INTEGER NOT NULL,
    daily_breakdown JSONB NOT NULL,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (patient_id, period_start, period_type)
);

CREATE TABLE report_insights (
    id              BIGSERIAL PRIMARY KEY,
    report_id       BIGINT NOT NULL REFERENCES health_reports(id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL
                        CHECK (type IN ('WARNING','RECOMMENDATION','TREND')),
    severity        VARCHAR(10) NOT NULL
                        CHECK (severity IN ('INFO','WARN','CRITICAL')),
    title           VARCHAR(200) NOT NULL,
    description     TEXT NOT NULL,
    source          VARCHAR(100) NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_health_reports_patient ON health_reports (patient_id, period_start DESC);
CREATE INDEX idx_health_reports_group ON health_reports (care_group_id, period_start DESC);
CREATE INDEX idx_report_insights_report ON report_insights (report_id, severity);
