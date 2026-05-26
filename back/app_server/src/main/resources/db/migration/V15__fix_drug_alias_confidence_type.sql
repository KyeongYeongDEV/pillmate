-- V13 created confidence as SMALLINT; Hibernate maps int -> INTEGER.
-- Widen to INTEGER to match entity mapping.
ALTER TABLE drug_alias ALTER COLUMN confidence TYPE INTEGER;
