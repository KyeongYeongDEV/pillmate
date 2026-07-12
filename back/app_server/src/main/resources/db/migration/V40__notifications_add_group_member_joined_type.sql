-- V40: notifications.type CHECK 제약 확장 — GROUP_MEMBER_JOINED 추가 (그룹 가입 실시간 알림)
-- CHECK 재생성은 V24/V27 패턴 그대로 (기존 row 영향 없음, db-safety 준수 — DROP/ADD CONSTRAINT 만, 데이터 변경 없음)
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type
        CHECK (type IN (
            'DOSE_TAKEN',
            'DOSE_MISSED',
            'DOSE_CANCELED',
            'DDI_CRITICAL',
            'PRESCRIPTION_NEW',
            'WEEKLY_REPORT',
            'GROUP_MEMBER_JOINED'
        ));
