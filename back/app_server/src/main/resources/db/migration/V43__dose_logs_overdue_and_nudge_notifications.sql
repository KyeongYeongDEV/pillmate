-- T-BE-GROUP-CARE-NUDGE: 그룹원이 미복용 그룹원을 챙기는 알림 기능
-- 1) dose_logs.overdue_notified_at — 예정시각+30분 경과 PENDING 알림 발송 시각 (폴러 idempotent 기준, group_notified_at/V27·reminded_at/V42 선례)
-- 2) notifications.type CHECK 확장 — DOSE_OVERDUE(지연 알림), DOSE_NUDGE(수동 넛지) 추가
-- ADD COLUMN / CREATE INDEX / CHECK 재생성만 수행. 기존 데이터 변경/삭제 없음 (db-safety 준수, V27/V40/V42 패턴 동일)

ALTER TABLE dose_logs ADD COLUMN overdue_notified_at TIMESTAMPTZ;

CREATE INDEX idx_dose_logs_overdue_notify_due
    ON dose_logs (scheduled_at)
    WHERE status = 'PENDING' AND overdue_notified_at IS NULL;

ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type
        CHECK (type IN (
            'DOSE_REMINDER',
            'DOSE_TAKEN',
            'DOSE_MISSED',
            'DOSE_CANCELED',
            'DOSE_OVERDUE',
            'DOSE_NUDGE',
            'DDI_CRITICAL',
            'PRESCRIPTION_NEW',
            'WEEKLY_REPORT',
            'GROUP_MEMBER_JOINED'
        ));
