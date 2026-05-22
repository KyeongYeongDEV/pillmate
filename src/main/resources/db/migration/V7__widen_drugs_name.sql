-- V7: drugs.name 길이 확대 (VARCHAR(200) → VARCHAR(500))
--   사유: 식약처 제품허가/낱알식별 API 의 ITEM_NAME 에 200자 초과 케이스 존재
--   (성분명·함량·제형·괄호 보충이 모두 들어가면 300자 가까운 행 다수).
--   tsv 가 generated stored 라 직접 ALTER 가 막힘 → DROP/ALTER/ADD 패턴.
--   기존 V3 마이그레이션은 수정하지 않는다 (Flyway 불변성).
ALTER TABLE drugs DROP COLUMN tsv;
ALTER TABLE drugs ALTER COLUMN name TYPE VARCHAR(500);
ALTER TABLE drugs ADD COLUMN tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, (name::text || ' '::text) || COALESCE(ingredient, ''::text))) STORED;
CREATE INDEX idx_drugs_tsv ON drugs USING GIN (tsv);
