-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 한글 검색용 트라이그램 확장
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- full-text search 설정 확인
SELECT extname FROM pg_extension WHERE extname IN ('vector', 'pg_trgm');

-- 서버/DB 시간 KST 정착 (fresh 볼륨 최초 부팅 시 자동 반영). 기존 볼륨은 ALTER SYSTEM 1회 수동 실행 필요.
-- TIMESTAMPTZ 저장값은 UTC offset 그대로 — display/log 만 KST 로 시프트. (rules/common/db-safety.md 준수)
ALTER SYSTEM SET timezone = 'Asia/Seoul';
SELECT pg_reload_conf();
