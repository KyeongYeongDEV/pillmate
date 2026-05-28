CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL,
    actor_user_id   BIGINT NOT NULL,
    care_group_id   BIGINT NOT NULL,
    type            VARCHAR(40) NOT NULL,
    title           VARCHAR(120) NOT NULL,
    body            VARCHAR(280) NOT NULL,
    dose_log_id     BIGINT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    read_at         TIMESTAMPTZ,
    CONSTRAINT chk_notifications_type   CHECK (type   IN ('DOSE_TAKEN', 'DOSE_MISSED')),
    CONSTRAINT chk_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'READ'))
);

CREATE INDEX idx_notifications_recipient_time ON notifications (recipient_user_id, created_at DESC);
CREATE INDEX idx_notifications_dose_log ON notifications (dose_log_id);
