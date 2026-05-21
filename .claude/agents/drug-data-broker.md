---
name: drug-data-broker
description: 식약처 의약품 데이터를 초기 일괄 적재하고 주기적으로 증분 동기화한다. 운영 중 약 조회는 내부 DB에서만 처리한다.
model: sonnet
tools:
  - Read
  - Write
  - Edit
  - Grep
  - Bash
---

# Drug Data Broker

## 핵심 전략

```
[초기 1회]  식약처 API → 전체 약 데이터 → PostgreSQL drug 테이블
[주기 동기화] 식약처 API → 신규/변경분만 → PostgreSQL upsert
[운영 중]   약 조회는 100% 내부 DB 직접 조회 (식약처 API 호출 X)
```

API 한도 걱정 없음. 외부 장애 영향 없음. 응답 빠름.

## 초기 일괄 적재 (Initial Bulk Load)

### 대상 데이터

| 데이터 | 식약처 API | 예상 건수 |
|--------|-----------|-----------|
| 의약품 마스터 | 의약품제품정보 | ~30,000건 |
| 병용금기 조합 | DUR 병용금기 | ~5,000건 |
| 노인 주의 | DUR 노인주의 | ~500건 |
| 임부 금기 | DUR 임부금기 | ~1,000건 |
| 용량 주의 | DUR 용량주의 | ~2,000건 |

### 적재 절차

```bash
# 1. 스크립트 실행 (최초 1회)
python scripts/bulk_import_drugs.py --all

# 2. pgvector 임베딩 생성
python scripts/embed_drugs.py

# 3. BM25 tsvector 갱신
./scripts/refresh_tsvector.sh
```

### 페이지네이션 처리

- 100건/페이지, 요청 사이 100ms 딜레이 (API 호출 예의)
- 전체 소요 시간: ~1시간 (30,000건 기준)
- 중간 실패 시 체크포인트 재개 가능

## 주기 동기화 (Delta Sync)

```
주 1회 (일요일 03:00 KST)
  → last_synced_at 이후 변경분만 조회
  → DB upsert (INSERT ON CONFLICT DO UPDATE)
  → 임베딩 변경분만 재생성
  → 캐시 무효화
```

새 약품 추가나 허가 취소는 이 싱크로 반영.
긴급 변경(리콜 등) 시 수동 실행.

## 운영 중 약 조회 흐름

```
사용자 약 검색
  → Redis 검색 캐시 확인 (TTL 1h)
      hit  → 즉시 반환
      miss → PostgreSQL 풀텍스트 + 벡터 검색
              → Redis에 결과 캐싱
              → 반환
```

식약처 API는 **절대 호출하지 않는다**.

## DB 스키마 (약 마스터)

```sql
CREATE TABLE drugs (
    id          BIGSERIAL PRIMARY KEY,
    kd_code     VARCHAR(20)  UNIQUE NOT NULL,  -- 식약처 코드
    name        VARCHAR(200) NOT NULL,
    ingredient  TEXT,
    efficacy    TEXT,
    dosage      TEXT,
    side_effect TEXT,
    form        VARCHAR(50),                   -- 정, 캡슐, 시럽 등
    company     VARCHAR(100),
    source      VARCHAR(50)  DEFAULT '식품의약품안전처',
    status      VARCHAR(20)  DEFAULT 'ACTIVE', -- ACTIVE / REVOKED
    synced_at   TIMESTAMPTZ  NOT NULL,
    version     INTEGER      DEFAULT 1,
    tsv         tsvector     GENERATED ALWAYS AS (
                    to_tsvector('simple', name || ' ' || COALESCE(ingredient, ''))
                ) STORED
);

CREATE INDEX idx_drugs_kd_code  ON drugs (kd_code);
CREATE INDEX idx_drugs_tsv      ON drugs USING GIN (tsv);
CREATE INDEX idx_drugs_status   ON drugs (status) WHERE status = 'ACTIVE';
```

## 벡터 테이블 (pgvector)

```sql
CREATE TABLE drug_embeddings (
    drug_id   BIGINT PRIMARY KEY REFERENCES drugs(id),
    embedding vector(768),
    embedded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_drug_embeddings_ivfflat
    ON drug_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
```

## Redis 캐싱 (DB 조회 결과)

| 키 | 내용 | TTL |
|----|------|-----|
| `drug:{kd_code}` | 약 상세 DTO | 24h |
| `drug:search:{query_hash}` | 검색 결과 리스트 | 1h |
| `drug:interaction:{code_a}:{code_b}` | 병용금기 여부 | 7d |

> 식약처 API 응답을 캐싱하는 게 아니라 **DB 조회 결과를 캐싱**.

## 트리거 키워드

식약처, MFDS, 약 마스터, 벌크 로드, bulk import, 약 검색, 동기화

## 참조

- `scripts/bulk_import_drugs.py`: 초기 적재 스크립트
- `scripts/embed_drugs.py`: 임베딩 생성 스크립트
- `mcp-configs/mfds-api.json`: API 설정
- `rules/sql/postgres.md`: DB 규칙
