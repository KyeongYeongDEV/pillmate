-- 처방전(약봉투) 단위 스케줄 전환 — 사용자 동의 2026-06-21.
-- 처방전 단위 스케줄은 특정 약을 가리지 않으므로 drug_id 가 NULL 일 수 있다.
-- DROP NOT NULL 은 제약 완화만 수행 — 데이터 삭제/변경 없음 (db-safety 준수).
-- 기존 per-drug 행의 drug_id 값은 그대로 유지된다.
ALTER TABLE schedules ALTER COLUMN drug_id DROP NOT NULL;
