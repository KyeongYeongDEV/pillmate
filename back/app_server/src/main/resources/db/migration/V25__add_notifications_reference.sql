ALTER TABLE notifications
    ADD COLUMN reference_id   BIGINT      NULL,
    ADD COLUMN reference_type VARCHAR(30) NULL;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_reference_type
    CHECK (reference_type IS NULL
        OR reference_type IN ('PRESCRIPTION', 'REPORT', 'DOSE_LOG', 'CARE_GROUP'));
