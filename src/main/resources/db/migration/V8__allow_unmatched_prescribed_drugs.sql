-- T-OCR-FIX3: drugId null 허용 + name_raw NOT NULL + CHECK 제약
-- 매칭 실패한 OCR 항목도 PrescribedDrug 으로 저장 (ocrStatus = MANUAL 강제)
ALTER TABLE prescribed_drugs
    ALTER COLUMN drug_id DROP NOT NULL;

UPDATE prescribed_drugs SET name_raw = 'unknown' WHERE name_raw IS NULL;

ALTER TABLE prescribed_drugs
    ALTER COLUMN name_raw SET NOT NULL;

ALTER TABLE prescribed_drugs
    ADD CONSTRAINT chk_prescribed_drugs_namematch
    CHECK (drug_id IS NOT NULL OR name_raw IS NOT NULL);

CREATE INDEX idx_prescribed_drugs_unmatched
    ON prescribed_drugs (prescription_id)
    WHERE drug_id IS NULL;
