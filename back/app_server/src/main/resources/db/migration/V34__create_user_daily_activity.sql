-- V34: DAU/MAU 집계용 사용자 일별 활동 테이블 (user_id+date만, 환자 건강정보 없음)
-- best-effort upsert: INSERT ... ON CONFLICT DO NOTHING 으로 하루 1행 보장

CREATE TABLE user_daily_activity (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    active_date DATE         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_daily_activity UNIQUE (user_id, active_date)
);

CREATE INDEX idx_user_daily_activity_active_date ON user_daily_activity (active_date);
