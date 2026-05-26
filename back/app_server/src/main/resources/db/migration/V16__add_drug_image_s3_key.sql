ALTER TABLE drugs
    ADD COLUMN image_s3_key VARCHAR(255);

CREATE INDEX idx_drugs_image_s3_key ON drugs(image_s3_key) WHERE image_s3_key IS NOT NULL;

COMMENT ON COLUMN drugs.image_s3_key IS '식약처 약 이미지를 S3에 캐시한 객체 키 (drugs/images/{kdCode}.jpg). NULL이면 item_image fallback';
