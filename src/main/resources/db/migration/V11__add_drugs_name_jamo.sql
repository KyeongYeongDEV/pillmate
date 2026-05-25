ALTER TABLE drugs ADD COLUMN name_jamo VARCHAR(1200);

CREATE INDEX idx_drugs_name_jamo_trgm
    ON drugs USING GIN (name_jamo gin_trgm_ops);

-- ETL 백필 후 NOT NULL 강화는 별도 (V12)
