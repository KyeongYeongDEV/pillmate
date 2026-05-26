# PostgreSQL 복구 절차

> **의료 데이터 포함 — 사용자 명시 동의 후에만 실행할 것**
> 복구는 운영 DB를 변경합니다. 반드시 CTO와 사용자에게 확인 후 진행하세요.

---

## 전제 조건

- Docker 실행 중 (`docker ps | grep pillmate-postgres`)
- 복구 대상 덤프 파일: `back/.backups/postgres-YYYY-MM-DD-HHmm.dump.gz`
- `pillmate-app` 컨테이너 중지 (중복 연결 방지)

---

## 시나리오 1: 실수 삭제 복구 (가장 흔한 경우)

예: 에이전트가 테이블 데이터를 삭제한 경우 (2026-05-25 drug_embeddings 사고 재발 방지)

```bash
# 1) 앱 컨테이너 중지
docker stop pillmate-app

# 2) 복구할 덤프 확인
ls -lh back/.backups/

# 3) 덤프 압축 해제 후 복구 (--clean: 기존 객체 drop 후 재생성, --if-exists: drop 오류 무시)
gunzip -c back/.backups/postgres-YYYY-MM-DD-HHmm.dump.gz \
  | docker exec -i pillmate-postgres \
      pg_restore -U pillmate -d pillmate --clean --if-exists --no-acl --no-owner

# 4) 앱 재시작
docker start pillmate-app

# 5) 헬스 확인
docker ps | grep pillmate-app
```

---

## 시나리오 2: Flyway 마이그레이션 롤백

신규 마이그레이션 적용 후 오류가 발생한 경우.

```bash
# 1) 앱 중지
docker stop pillmate-app

# 2) 마이그레이션 적용 전 덤프로 복구 (시나리오 1 동일)
gunzip -c back/.backups/postgres-YYYY-MM-DD-HHmm.dump.gz \
  | docker exec -i pillmate-postgres \
      pg_restore -U pillmate -d pillmate --clean --if-exists --no-acl --no-owner

# 3) flyway_schema_history 확인 (복구 후 버전 상태 검증)
docker exec pillmate-postgres psql -U pillmate -d pillmate \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

# 4) 문제 마이그레이션 수정 후 앱 재시작
docker start pillmate-app
```

---

## 시나리오 3: Disaster Recovery (전체 손실)

볼륨 삭제 또는 서버 장애로 DB 전체 손실 시.

```bash
# 1) 컨테이너/볼륨 재생성 (docker-compose.yml 기준)
cd back && docker compose down
docker volume rm pillmate_postgres_data 2>/dev/null || true
docker compose up -d postgres
sleep 5  # postgres 초기화 대기

# 2) 덤프 복구
gunzip -c .backups/postgres-YYYY-MM-DD-HHmm.dump.gz \
  | docker exec -i pillmate-postgres \
      pg_restore -U pillmate -d pillmate --no-acl --no-owner

# 3) 앱 재시작
docker compose up -d
```

---

## 복구 후 검증

```bash
# 주요 테이블 레코드 수 확인
docker exec pillmate-postgres psql -U pillmate -d pillmate -c "
  SELECT 'drugs' AS tbl, COUNT(*) FROM drugs
  UNION ALL SELECT 'drug_alias', COUNT(*) FROM drug_alias
  UNION ALL SELECT 'drug_embeddings', COUNT(*) FROM drug_embeddings
  UNION ALL SELECT 'drug_interactions', COUNT(*) FROM drug_interactions;
"
```

---

## Phase 2+ 진화 계획

| Phase | 추가 내용 |
|-------|----------|
| Phase 2 | S3 업로드 (SSE-S3 암호화) + 보관 30일 |
| Phase 2 | AWS RDS automated backup (PITR 7일) |
| Phase 3 | PITR (Point-In-Time Recovery) — 1초 단위 복구 |
| Phase 4 | Multi-AZ RDS + cross-region 백업 |
