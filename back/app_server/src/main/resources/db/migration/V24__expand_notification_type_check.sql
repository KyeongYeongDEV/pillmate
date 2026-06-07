-- V24: notifications.type CHECK 제약 확장 (DDI_CRITICAL / PRESCRIPTION_NEW / WEEKLY_REPORT)
-- 기존 DOSE_TAKEN/DOSE_MISSED row 영향 없음 (db-safety 준수)
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type
        CHECK (type IN (
            'DOSE_TAKEN',
            'DOSE_MISSED',
            'DDI_CRITICAL',
            'PRESCRIPTION_NEW',
            'WEEKLY_REPORT'
        ));
