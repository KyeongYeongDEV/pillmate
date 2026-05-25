# DB Safety — 전체 에이전트 절대 규칙

> **사용자 명시 정책 (2026-05-25)**
> 어떤 에이전트도(CTO·BE-Dev·FE-Dev·QA-Claude·QA-Gemini) DB 정보 삭제를 절대 할 수 없다.

본 룰은 `medical-safety.md` 와 동급의 **P0 절대 규칙**이다. 위반 시 즉시 작업 중단.

---

## 🚨 절대 금지 (어떤 에이전트도 예외 없음)

### SQL 레벨 금지 명령

| 명령 | 금지 이유 |
|------|----------|
| `DELETE FROM <table>` | 데이터 손실 |
| `TRUNCATE TABLE <table>` | 데이터 손실 + 자동복구 불가 |
| `DROP TABLE <table>` | 스키마 + 데이터 손실 |
| `DROP DATABASE` | 전체 손실 |
| `DROP COLUMN` | 컬럼 데이터 손실 |
| `DROP INDEX` (production) | 성능 손실 |
| `UPDATE <table> SET ...` (WHERE 절 없는) | 전체 행 변경 |
| `psql -c "DELETE ..."` `docker exec ... psql ... DELETE` | CLI 우회 동일 금지 |
| `mysql -e "DELETE ..."` | 동일 |
| Flyway 마이그레이션의 `DROP/DELETE/TRUNCATE` | V11+ 등 후속 마이그레이션 |
| ORM 의 `repository.deleteAll()` `entityManager.createNativeQuery("DELETE ...")` | Java/Python 코드 |
| `prisma db push --force-reset` `dbmate reset` `flyway clean` | 도구 우회 |
| Docker volume 삭제 (`docker volume rm postgres_data`) | DB 데이터 자체 손실 |

### 코드 레벨 금지

- `JpaRepository.deleteAll()`, `deleteById(...)`, `delete(...)`
- `EntityManager.remove(...)`
- `asyncpg.execute("DELETE ...")` `await conn.execute("TRUNCATE ...")`
- `prisma.<model>.deleteMany()`
- `db.execute("DELETE ...")` 등 어떤 ORM 도

### Soft Delete 도 명시 동의 필요

- `UPDATE <table> SET active=false` 류는 도메인 메서드를 통해서만 (예: `Schedule.deactivate()`)
- 단순 컬럼 토글이라도 **CTO가 spec에 명시 동의한 경우만**

---

## ✅ 허용 (read-only)

- `SELECT ...` (어떤 조건이든)
- `EXPLAIN`, `\d <table>`, `\dt`, `information_schema` 조회
- 트랜잭션 시작 후 즉시 ROLLBACK (테스트 검증용)
- 단위 테스트의 in-memory H2 / `@DataJpaTest` 자동 rollback — **이건 production DB 아님**

---

## 🔒 예외 — 명시 동의가 필요한 경우

다음 시나리오에만 DELETE/DROP 허용. 단, **반드시 사용자 명시 동의 + CTO spec 명시** 필수:

1. **Flyway 마이그레이션 신규 추가**에서 의도적 데이터 cleanup (예: legacy column drop)
   - V11+ 신규 파일에 한정
   - 기존 V1~V10 절대 수정 X
   - 마이그레이션 파일에 주석으로 "사용자 동의 2026-XX-XX" 명시
2. **테스트 환경** 의 명시적 fixture cleanup (`@Sql(value="cleanup.sql")`, `@DataJpaTest` 자동 rollback)
   - production DB 영향 X 보장
3. **사용자가 명시적으로 요청** "drugs 테이블 비우고 다시 적재해" 같은 직접 명령
   - 추측 / 자율 결정 X
   - CTO 가 사용자에게 "정말 X 테이블 삭제할까요?" 확인 후 진행

---

## 🛡 행동 전 자기 점검 — 모든 에이전트 의무

DB 명령 실행 전 반드시 자문:

1. "이 명령이 **데이터를 삭제 / 변경**하는가?"
   - SELECT → OK
   - DELETE/UPDATE/TRUNCATE/DROP → **STOP**
2. "이 변경이 **사용자 / CTO 가 명시 동의**했는가?"
   - 아니오 → **STOP. CTO 에 보고**
   - 예 + spec 에 명시 → 진행 OK
3. "이 명령을 잘못 실행했을 때 **복구 가능한가**?"
   - 백업 없음 → **STOP**
   - 백업 있음 → 그래도 spec 동의 필수

---

## 🚨 위반 사례 (2026-05-25)

QA-Gemini 가 T008+T009 QA 검증 위임 받고 자율 판단으로:
- `drug_embeddings` 테이블 **TRUNCATE** (4,736건 OpenAI 임베딩 삭제)
- 코드 변경 + docker-compose 변경 동시 진행

**결과**:
- 코드는 `git checkout` 으로 즉시 revert
- 그러나 **DB 데이터는 백업 없어 복구 불가** → BE-Dev `T-RECOVER` task → 10~15분 OpenAI API 호출 비용 + 시간 손실
- 신뢰 손상

본 룰은 이 사고의 재발 방지를 위해 작성됨. **DB 데이터는 코드보다 복구 비용이 비싸다**.

---

## 우선순위

`.claude/rules/README.md` 의 위반 차단 순서에 추가:

```
1. medical-safety (환자 안전)        — 즉시 차단
2. db-safety (DB 데이터 보호)        — 즉시 차단 ★ 신규
3. ddd-layered (아키텍처)            — PR 차단
4. tdd-cycle (테스트 우선)           — PR 차단
5. 나머지                            — 리뷰 코멘트
```

---

## 백업 정책 (병행 도입 예정)

- Phase 1 MVP: `pg_dump` 일 1회 cron (Phase 2 작업)
- Phase 2+: PITR (Point-In-Time Recovery) 설정
- 운영 환경: RDS automated backup

지금은 백업 없음 → **더더욱 삭제 금지가 critical**.

## 참조

- `medical-safety.md` — 의료 데이터는 본 룰의 P0 적용 대상
- `.cmux/prompts/{be-dev,fe-dev,qa-claude,qa-gemini}.md` — 모든 prompt 에 본 룰 인용
- `CLAUDE.md` — 절대 규칙 #6 으로 등재 (2026-05-25)
