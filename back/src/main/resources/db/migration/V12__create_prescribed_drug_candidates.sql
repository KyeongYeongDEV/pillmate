CREATE TABLE prescribed_drug_candidates (
    id               BIGSERIAL PRIMARY KEY,
    prescription_id  BIGINT       NOT NULL REFERENCES prescriptions(id) ON DELETE CASCADE,
    item_index       INTEGER      NOT NULL,
    decision_type    VARCHAR(10)  NOT NULL CHECK (decision_type IN ('CONFIRM', 'MANUAL')),
    reason           VARCHAR(30)  NOT NULL,
    options_json     JSONB        NOT NULL,
    resolved_drug_id BIGINT,
    resolved_at      TIMESTAMPTZ,
    resolved_by      BIGINT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pdc_unresolved
    ON prescribed_drug_candidates (prescription_id)
    WHERE resolved_drug_id IS NULL;
