CREATE TABLE prescriptions (
    id            BIGSERIAL PRIMARY KEY,
    care_group_id BIGINT NOT NULL,
    patient_id    BIGINT NOT NULL,
    image_key     VARCHAR(500),
    prescribed_at DATE NOT NULL,
    ocr_status    VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (ocr_status IN ('PENDING','PROCESSING',
                                            'DONE','FAILED','MANUAL')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_prescriptions_group   ON prescriptions (care_group_id);
CREATE INDEX idx_prescriptions_patient ON prescriptions (patient_id);

CREATE TABLE prescribed_drugs (
    id              BIGSERIAL PRIMARY KEY,
    prescription_id BIGINT NOT NULL REFERENCES prescriptions(id)
                        ON DELETE CASCADE,
    drug_id         BIGINT NOT NULL REFERENCES drugs(id),
    name_raw        VARCHAR(200),
    dose_amount     NUMERIC(8,2),
    dose_unit       VARCHAR(20),
    frequency       INTEGER NOT NULL DEFAULT 3,
    duration_days   INTEGER,
    confidence      NUMERIC(4,3),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_prescribed_drugs_prescription ON prescribed_drugs (prescription_id);
