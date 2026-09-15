-- 그룹원이 "복용 완료" 활동에 칭찬 보내기. (activity_feed_id, praiser_user_id) 유니크로
-- 같은 사람이 같은 활동을 두 번 눌러도 알림이 중복 발송되지 않는다(멱등, DB 레벨 보장).
CREATE TABLE activity_praises (
    id               BIGSERIAL PRIMARY KEY,
    activity_feed_id BIGINT      NOT NULL REFERENCES activity_feeds(id),
    praiser_user_id  BIGINT      NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_activity_praises_activity_praiser UNIQUE (activity_feed_id, praiser_user_id)
);

CREATE INDEX idx_activity_praises_activity ON activity_praises (activity_feed_id);

-- notifications.type CHECK 확장 — DOSE_PRAISE 추가 (V43 패턴 동일, 데이터 변경 없음)
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
            'DOSE_PRAISE',
            'DDI_CRITICAL',
            'PRESCRIPTION_NEW',
            'WEEKLY_REPORT',
            'GROUP_MEMBER_JOINED'
        ));

-- notifications.reference_type CHECK 확장 — ACTIVITY_FEED 추가 (칭찬 알림이 어느 활동에 대한 것인지 참조)
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_reference_type;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_reference_type
        CHECK (reference_type IS NULL
            OR reference_type IN ('PRESCRIPTION', 'REPORT', 'DOSE_LOG', 'CARE_GROUP', 'ACTIVITY_FEED'));
