-- V27: #138 복약 취소 정책 + 서버 권위 60초 그룹 알림
-- 1) dose_logs.group_notified_at — 그룹 알림 발송 시각 (폴러 idempotent 기준)
-- 2) notifications.type CHECK 확장 — DOSE_CANCELED 추가
-- ADD COLUMN / CREATE INDEX only. CHECK 재생성은 V24 패턴 (기존 row 영향 없음, db-safety 준수)

ALTER TABLE dose_logs ADD COLUMN group_notified_at TIMESTAMPTZ;

CREATE INDEX idx_dose_logs_group_notify_due
    ON dose_logs (checked_at)
    WHERE status = 'TAKEN' AND group_notified_at IS NULL;

ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type
        CHECK (type IN (
            'DOSE_TAKEN',
            'DOSE_MISSED',
            'DOSE_CANCELED',
            'DDI_CRITICAL',
            'PRESCRIPTION_NEW',
            'WEEKLY_REPORT'
        ));
