-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 한글 검색용 트라이그램 확장
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- full-text search 설정 확인
SELECT extname FROM pg_extension WHERE extname IN ('vector', 'pg_trgm');
