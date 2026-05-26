-- V17: (1) prescriptions.care_group_id → NULLABLE (소유 모델 변경: group→user)
--       (2) activity_feeds 테이블 신규 생성

-- DB safety: DROP COLUMN 절대 X (사용자 명시 동의 2026-05-26, spec T-DOMAIN-PIVOT-USER-OWNED)
ALTER TABLE prescriptions ALTER COLUMN care_group_id DROP NOT NULL;

COMMENT ON COLUMN prescriptions.care_group_id IS
    '의미 변경 (2026-05-26): 그룹 소유 → 등록 시 view 컨텍스트 metadata. 향후 Phase 2+에서 DROP 검토.';

-- 신규 도메인: activity_feeds
CREATE TABLE activity_feeds (
    id             BIGSERIAL PRIMARY KEY,
    actor_user_id  BIGINT       NOT NULL,
    activity_type  VARCHAR(40)  NOT NULL,
    reference_id   BIGINT,
    summary        TEXT         NOT NULL,
    severity       VARCHAR(20)  DEFAULT 'INFO',
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_activity_feeds_actor_time
    ON activity_feeds (actor_user_id, occurred_at DESC);
