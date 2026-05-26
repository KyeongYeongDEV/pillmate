# PillMate DB 백업 운영 가이드

> 사고 이력: 2026-05-25 QA-Gemini 자율 판단으로 `drug_embeddings` 4,736건 TRUNCATE.
> 백업 없어 복구 불가 → OpenAI API 재적재 10~15분 + 비용 손실.
> 본 가이드는 해당 사고 재발 방지를 위한 P0 운영 정책입니다.

---

## 구조

```
back/
├── .backups/                    # ★ git 적재 금지 (.gitignore 등록)
│   └── postgres-YYYY-MM-DD-HHmm.dump.gz
└── scripts/
    ├── backup_postgres.sh       # 백업 실행
    ├── verify_backup.sh         # 무결성 검증
    └── restore_postgres.md      # 복구 절차
```

---

## 백업 스크립트 실행

### 수동 즉시 실행

```bash
bash back/scripts/backup_postgres.sh
```

### 자동화 (host crontab — 매일 02:00)

```bash
crontab -e
```

아래 라인 추가:

```
0 2 * * * /Users/user/Downloads/pillmate/back/scripts/backup_postgres.sh >> /tmp/pillmate-backup.log 2>&1
```

등록 확인:

```bash
crontab -l | grep pillmate
```

---

## 보관 정책

| 항목 | 정책 |
|------|------|
| 보관 기간 | **7일** (8일 이상 된 파일 자동 삭제) |
| 포맷 | pg_dump Custom (pg_restore 호환) |
| 압축 | gzip -9 |
| 권한 | chmod 600 (소유자 전용) |
| git 적재 | **절대 금지** (의료 데이터 PII 포함) |

---

## 백업 검증

```bash
bash back/scripts/verify_backup.sh
```

출력 예:

```
[verify] 2026-05-26 02:00:31 검증 시작
[verify] 대상 파일: postgres-2026-05-26-0200.dump.gz
[verify] 크기: 64M — OK
[verify] gzip 무결성: OK
[verify] 테이블 데이터 18건 확인 — OK
[verify] PASS: postgres-2026-05-26-0200.dump.gz
```

---

## 복구 절차 요약

자세한 절차: `back/scripts/restore_postgres.md`

| 시나리오 | 명령 요약 |
|----------|----------|
| 실수 삭제 | `docker stop pillmate-app` → `gunzip -c dump.gz \| docker exec -i pillmate-postgres pg_restore ...` → `docker start pillmate-app` |
| 마이그레이션 롤백 | 위 동일 + flyway_schema_history 확인 |
| Disaster | `docker compose down` → volume rm → compose up postgres → pg_restore |

> **중요**: 복구는 반드시 사용자 명시 동의 후 실행. db-safety.md P0 정책 준수.

---

## 보안 주의사항

- `back/.backups/` 는 환자 처방 데이터 포함 → **외부 전송 절대 금지**
- Phase 2에서 S3 SSE-S3 암호화 업로드 + CloudTrail 감사 도입 예정
- 백업 서버 접근은 최소 권한 원칙 적용

---

## Phase별 진화 계획

| Phase | 추가 내용 | 비용 영향 |
|-------|----------|----------|
| **1 (현재)** | host crontab pg_dump, 7일 로컬 보관 | $0 |
| **2** | S3 업로드 (SSE-S3), 30일 보관, 자동 IA 전환 | ~$2/월 |
| **2** | RDS automated backup (PITR 7일) | RDS 비용에 포함 |
| **3** | PITR 확장 (14일), cross-region 복제 | ~$10/월 추가 |
| **4** | Multi-AZ + automated failover | 아키텍처 결정 시점에 재산정 |
