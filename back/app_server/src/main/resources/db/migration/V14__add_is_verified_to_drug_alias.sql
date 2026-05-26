ALTER TABLE drug_alias ADD COLUMN is_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_drug_alias_verified ON drug_alias (id)
    WHERE is_verified = TRUE;
