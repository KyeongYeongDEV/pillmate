-- 약봉투-스케줄 링크 — 사용자 동의 2026-06-20.
-- schedules.prescription_id 컬럼은 V5 에서 이미 정의(nullable)되어 있으나 코드 매핑/인덱스/FK 가 없었다.
-- 본 마이그레이션은 컬럼 추가가 아니라 (1) 약봉투 단위 조회용 인덱스 (2) 무결성용 FK 만 추가한다.
-- 데이터 삭제/덮어쓰기 없음 — 기존 행의 prescription_id 는 NULL 그대로 유지된다 (db-safety 준수).
CREATE INDEX IF NOT EXISTS idx_schedules_prescription
    ON schedules (prescription_id, active);

ALTER TABLE schedules
    ADD CONSTRAINT fk_schedules_prescription
    FOREIGN KEY (prescription_id) REFERENCES prescriptions(id);
