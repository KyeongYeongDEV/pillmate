-- 한 사용자당 핀된 그룹 1개 DB 레벨 강제 (race condition 방지)
-- 사전 검증: SELECT user_id, count(*) FROM memberships WHERE is_pinned=TRUE GROUP BY user_id HAVING count > 1 → 0건
-- db-safety: 기존 idx_memberships_user_pinned 유지 (DROP X). UNIQUE 추가만.
CREATE UNIQUE INDEX uq_memberships_pinned ON memberships(user_id) WHERE is_pinned = TRUE;
