-- pgvector (RAG 임베딩 검색)
CREATE EXTENSION IF NOT EXISTS vector;

-- pg_trgm (한글 LIKE 검색 + GIN 인덱스)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
