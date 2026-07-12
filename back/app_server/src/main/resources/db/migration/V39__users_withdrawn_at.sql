-- 회원탈퇴 soft delete — withdrawn_at 설정 시 탈퇴(PII 익명화). NULL=활성.
-- db-safety: additive-only. 기존 행/데이터 변경 없음(신규 nullable 컬럼).
ALTER TABLE users ADD COLUMN withdrawn_at TIMESTAMPTZ NULL;

CREATE INDEX idx_users_withdrawn_at ON users (withdrawn_at);
