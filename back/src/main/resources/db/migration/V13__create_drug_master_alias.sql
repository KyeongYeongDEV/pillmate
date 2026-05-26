CREATE TABLE drug_master (
    item_seq        VARCHAR(20)     PRIMARY KEY,
    product_name    VARCHAR(500)    NOT NULL,
    ingredient_code VARCHAR(100),
    ingredient_name VARCHAR(500),
    dose_amount     NUMERIC(10, 3),
    dose_unit       VARCHAR(20),
    form            VARCHAR(50),
    company         VARCHAR(200),
    image_url       TEXT,
    source          VARCHAR(50),
    synced_at       TIMESTAMPTZ,
    legacy_drug_id  BIGINT          REFERENCES drugs(id) ON DELETE SET NULL
);

CREATE INDEX idx_drug_master_legacy ON drug_master (legacy_drug_id);

CREATE TABLE drug_alias (
    id          BIGSERIAL       PRIMARY KEY,
    alias       VARCHAR(500)    NOT NULL,
    alias_jamo  VARCHAR(1000),
    item_seq    VARCHAR(20)     NOT NULL REFERENCES drug_master(item_seq) ON DELETE CASCADE,
    source      VARCHAR(20)     NOT NULL CHECK (source IN ('product','ingredient','bundle','user')),
    confidence  SMALLINT        NOT NULL CHECK (confidence BETWEEN 0 AND 100),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_alias_item UNIQUE (alias, item_seq)
);

CREATE INDEX idx_alias_exact  ON drug_alias (alias);
CREATE INDEX idx_alias_jamo_trgm ON drug_alias USING GIN (alias_jamo gin_trgm_ops);
CREATE INDEX idx_alias_name_trgm ON drug_alias USING GIN (alias   gin_trgm_ops);
