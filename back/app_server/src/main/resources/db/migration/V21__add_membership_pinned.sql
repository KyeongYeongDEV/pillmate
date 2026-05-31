-- 케어그룹 핀(고정) — 한 사용자당 한 그룹만 핀 (application 레벨 강제)
ALTER TABLE memberships ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE;

-- 핀된 그룹 빠른 조회용 partial index
CREATE INDEX idx_memberships_user_pinned ON memberships(user_id) WHERE is_pinned = TRUE;
