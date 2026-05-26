CREATE TABLE drugs (
    id          BIGSERIAL PRIMARY KEY,
    kd_code     VARCHAR(20) UNIQUE NOT NULL,
    name        VARCHAR(200) NOT NULL,
    ingredient  TEXT,
    efficacy    TEXT,
    dosage      TEXT,
    side_effect TEXT,
    form        VARCHAR(50),
    company     VARCHAR(100),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','REVOKED')),
    source      VARCHAR(50) NOT NULL DEFAULT '식품의약품안전처',
    synced_at   TIMESTAMPTZ NOT NULL,
    version     INTEGER NOT NULL DEFAULT 1,
    tsv         tsvector GENERATED ALWAYS AS (
                    to_tsvector('simple',
                        name || ' ' || COALESCE(ingredient,''))
                ) STORED
);
CREATE INDEX idx_drugs_kd_code ON drugs (kd_code);
CREATE INDEX idx_drugs_tsv     ON drugs USING GIN (tsv);
CREATE INDEX idx_drugs_name_trgm ON drugs USING GIN (name gin_trgm_ops);
CREATE INDEX idx_drugs_active  ON drugs (status) WHERE status = 'ACTIVE';

CREATE TABLE drug_embeddings (
    drug_id     BIGINT PRIMARY KEY REFERENCES drugs(id),
    embedding   vector(768),
    embedded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_drug_embeddings_ivfflat
    ON drug_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE TABLE drug_interactions (
    id          BIGSERIAL PRIMARY KEY,
    drug_code_a VARCHAR(20) NOT NULL,
    drug_code_b VARCHAR(20) NOT NULL,
    type        VARCHAR(30) NOT NULL CHECK (
                    type IN ('DRUG_DRUG','AGE_ELDERLY','AGE_PEDIATRIC',
                             'PREGNANCY','LACTATION')),
    severity    VARCHAR(20) NOT NULL CHECK (
                    severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    description TEXT NOT NULL,
    source      VARCHAR(50) NOT NULL DEFAULT '식품의약품안전처',
    synced_at   TIMESTAMPTZ NOT NULL,
    UNIQUE (drug_code_a, drug_code_b, type)
);
CREATE INDEX idx_drug_interactions_pair ON drug_interactions (drug_code_a, drug_code_b);
