-- 그룹별 별명 — 같은 사용자도 그룹마다 다른 별명 표시 가능 (기본값 null = 실제 이름 폴백)
ALTER TABLE memberships ADD COLUMN nickname VARCHAR(20);
