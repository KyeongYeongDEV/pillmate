-- T-BE-SOLO-NOGROUP (2026-07-13): 케어그룹 없는 솔로 사용자도 약봉투 등록 허용.
-- 제약 완화만 수행 (데이터 변경/삭제 없음) — prescriptions.care_group_id V17 선례와 동일.
ALTER TABLE schedules ALTER COLUMN care_group_id DROP NOT NULL;
