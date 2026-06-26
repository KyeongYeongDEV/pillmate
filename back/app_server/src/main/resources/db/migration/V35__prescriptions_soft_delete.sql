ALTER TABLE prescriptions ADD COLUMN deleted_at TIMESTAMPTZ NULL;

CREATE INDEX idx_prescriptions_patient_deleted
    ON prescriptions (patient_id, deleted_at);
