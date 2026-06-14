-- 그룹 탈퇴(소프트삭제) 컬럼 추가. 사용자 동의 2026-06-14.
-- ADD COLUMN only — 기존 행은 DEFAULT 'ACTIVE' 로 backfill. 데이터 삭제/변경 없음 (db-safety 준수).
ALTER TABLE memberships ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE memberships ADD COLUMN left_at TIMESTAMPTZ;
ALTER TABLE memberships ADD CONSTRAINT chk_memberships_status CHECK (status IN ('ACTIVE', 'LEFT'));

CREATE INDEX idx_memberships_group_status ON memberships (care_group_id, status);
CREATE INDEX idx_memberships_user_status ON memberships (user_id, status);
