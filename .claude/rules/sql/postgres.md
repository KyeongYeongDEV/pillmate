---
name: postgres
description: PostgreSQL 사용 규칙 — pgvector, 파티셔닝, 인덱스
---

# PostgreSQL Rules

## 버전

- PostgreSQL 16+
- pgvector 0.7+
- pg_trgm (BM25용)

## 명명 규칙

- 테이블/컬럼: snake_case 복수형 (`prescriptions`, `dose_logs`)
- PK: `id` (BIGSERIAL)
- FK: `{referenced_table_singular}_id` (`prescription_id`)
- 인덱스: `idx_{table}_{columns}`
- 제약: `chk_{table}_{rule}`, `uq_{table}_{cols}`

## 컬럼 규칙

- 모든 시간: `TIMESTAMPTZ` (with time zone)
- 텍스트: `VARCHAR(N)` 명시 제한 또는 `TEXT`
- 금액/소수: `NUMERIC(p, s)` (FLOAT 금지)
- enum: `VARCHAR` + CHECK 제약 (Postgres enum은 변경 어려움)

## 파티셔닝

복용 로그(`dose_logs`)는 월 단위 파티션:
```sql
CREATE TABLE dose_logs (
    id BIGSERIAL,
    schedule_id BIGINT NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    ...
) PARTITION BY RANGE (scheduled_at);

CREATE TABLE dose_logs_2026_05 PARTITION OF dose_logs
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
```

- 6개월 이상 오래된 파티션은 분리 (Phase 3 cold storage)

## 인덱스

- 외래키 컬럼은 항상 인덱스
- 복합 인덱스 순서: 선택성 높은 컬럼 먼저
- 부분 인덱스 활용 (`WHERE status = 'PENDING'`)

## pgvector

```sql
CREATE TABLE drug_embeddings (
    drug_id BIGINT PRIMARY KEY,
    embedding vector(768),         -- text-embedding-004
    tsv tsvector
);

CREATE INDEX idx_drug_embeddings_ivfflat
    ON drug_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX idx_drug_embeddings_tsv
    ON drug_embeddings USING GIN (tsv);
```

- Phase 1: ivfflat (lists = 100)
- Phase 2+: HNSW 검토 (정확도 vs 메모리)

## RLS (Phase 2+ 검토)

- 그룹 격리는 Phase 1 application 레이어에서
- Phase 2에 RLS 도입 검토

## 마이그레이션

- Flyway 또는 dbmate
- 절대 기존 마이그레이션 수정 금지
- 큰 테이블 ALTER는 별도 PR + 운영 시간 검토

## 금지

- `SELECT *` (명시적 컬럼)
- 인덱스 없는 외래키
- timestamp without time zone
- FLOAT for money
- Postgres ENUM 타입 (확장 어려움)
