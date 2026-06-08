-- pg_trgm GIN index on drugs.ingredient for AsyncpgIngredientSearch ILIKE performance
CREATE INDEX IF NOT EXISTS idx_drugs_ingredient_trgm
    ON drugs USING GIN (ingredient gin_trgm_ops)
    WHERE ingredient IS NOT NULL;
