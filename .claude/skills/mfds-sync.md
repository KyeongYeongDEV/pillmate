---
name: mfds-sync
description: 식약처 의약품 데이터 초기 일괄 적재 및 주기 동기화 워크플로우.
---

# MFDS Sync

## 전략

```
초기 1회:   식약처 API → 전체 적재 → DB + pgvector 인덱스
주 1회 유지: 변경분만 delta sync
운영 중:    DB 직접 조회 (API 호출 없음)
```

## 초기 일괄 적재 (최초 1회 실행)

```bash
# 1. 전체 약 마스터 적재
python scripts/bulk_import_drugs.py --all

# 2. 벡터 임베딩 생성 (Gemini text-embedding-004)
python scripts/embed_drugs.py

# 3. BM25용 tsvector 갱신 (트리거로 자동, 명시 실행은 아래)
./scripts/refresh_tsvector.sh
```

예상 소요 시간: 1~2시간 (30,000건 기준)

### 중간 실패 재개

```bash
# 체크포인트에서 이어서 실행
python scripts/bulk_import_drugs.py --resume
```

### 체크리스트

- [ ] `MFDS_API_KEY` 환경변수 설정
- [ ] PostgreSQL 실행 중 + pgvector 확장 설치
- [ ] `drugs` / `drug_embeddings` 테이블 마이그레이션 완료
- [ ] 적재 완료 후 건수 검증 (예상 ~30,000건)
- [ ] 검색 테스트 (한글 약품명, 성분명)
- [ ] pgvector 인덱스 확인

## 주기 동기화 (주 1회 자동)

```
cron: 0 3 * * 0 (매주 일요일 03:00 KST)
```

### 단계

1. `last_synced_at` 이후 변경분 조회
2. DB upsert (`INSERT ... ON CONFLICT (kd_code) DO UPDATE`)
3. 변경된 약품 임베딩 재생성
4. Redis 관련 키 무효화

### 수동 실행 (긴급 리콜 등)

```bash
python scripts/bulk_import_drugs.py --delta-only --since 2026-05-01
```

## 검증

- [ ] 동기화 후 건수 변화 합리적 (이전 대비 ±5% 이하)
- [ ] 검색 회귀 테스트 통과 (`/pill-rag-eval`)
- [ ] 신규 약품 검색 가능

## 장애 대응

| 상황 | 조치 |
|------|------|
| API 한도 초과 | 대기 후 재시도 (운영 영향 없음, DB 사용 중) |
| API 장애 | 동기화 연기, 다음 주 수행 (기존 DB로 운영 지속) |
| DB 적재 실패 | 체크포인트 파일로 이어서 재실행 |

## 참조

- `agents/drug-data-broker.md`
- `scripts/bulk_import_drugs.py`
