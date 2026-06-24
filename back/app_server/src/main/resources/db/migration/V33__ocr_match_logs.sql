CREATE TABLE ocr_match_logs (
    id                      BIGSERIAL PRIMARY KEY,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    image_hash              VARCHAR(64),
    image_key               VARCHAR(300),
    raw_ocr_text            VARCHAR(300) NOT NULL,
    normalized_query        VARCHAR(300),
    matched_kd_code         VARCHAR(40),
    matched_drug_name       VARCHAR(300),
    decision                VARCHAR(20),
    final_score             NUMERIC(5,4),
    rrf_score               NUMERIC(8,5),
    reranker_score          NUMERIC(8,5),
    surfaced_by             VARCHAR(120),
    candidates_json         JSONB,
    matcher_version         VARCHAR(40),
    threshold               NUMERIC(4,3),
    gemini_raw_json         JSONB,
    latency_ms              INT,
    user_corrected_kd_code  VARCHAR(40)
);

CREATE INDEX idx_ocr_match_logs_image_hash ON ocr_match_logs (image_hash);
CREATE INDEX idx_ocr_match_logs_image_key  ON ocr_match_logs (image_key);
CREATE INDEX idx_ocr_match_logs_created_at ON ocr_match_logs (created_at);
