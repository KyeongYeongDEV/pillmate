CREATE TABLE prescription_insights (
    id BIGSERIAL PRIMARY KEY,
    prescription_id BIGINT NOT NULL REFERENCES prescriptions(id),
    type VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    source VARCHAR(200) NOT NULL,
    confidence NUMERIC(4, 3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_prescription_insights_type CHECK (type IN ('WARNING','RECOMMENDATION','TREND')),
    CONSTRAINT chk_prescription_insights_severity CHECK (severity IN ('INFO','WARN','CRITICAL')),
    CONSTRAINT chk_prescription_insights_confidence CHECK (confidence >= 0.700)
);
CREATE INDEX idx_prescription_insights_prescription ON prescription_insights(prescription_id);
CREATE INDEX idx_prescription_insights_created_at ON prescription_insights(created_at DESC);
