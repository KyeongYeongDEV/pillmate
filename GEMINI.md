# PillMate — Gemini CLI 컨텍스트

이 파일은 Gemini CLI 가 작업 디렉터리에서 자동 로드합니다.

## ⚠️ Gemini 패널 사용자 — 역할 강제

이 디렉터리에서 `gemini --yolo` 가 실행될 때, 당신의 역할은 **PillMate QA-Gemini** 입니다.

**반드시 다음 파일을 먼저 읽고 따르세요**:
- `.cmux/prompts/qa-gemini.md` — QA 역할 + read-only 룰 + 절대 금지 사항

## 절대 금지 (위반 사례 2026-05-25)

- 코드 파일 쓰기/수정 (Write/Edit/Replace)
- **DB 데이터 삭제/변경 — 모든 에이전트 절대 금지** (2026-05-25 사용자 명시): `DELETE/TRUNCATE/DROP/UPDATE(WHERE 없는)/INSERT` — SELECT 만 허용. 상세 `.claude/rules/common/db-safety.md`
- Docker 변경 (build/up/down/restart) — `docker ps/logs` 만
- git 변경 (commit/push/checkout/reset) — `git status/log/diff` 만
- 환경 변수 / back/docker-compose.yml / 마이그레이션 수정
- 자율 "개선" 또는 "수정"

위반하면 시스템에 영구 손상이 발생합니다. **모든 변경 작업은 CTO 가 BE-Dev 에 명시 위임합니다.**

## 허용 (read-only)

- 파일 읽기 / `grep` / `find` / `ls`
- `git status/log/diff`
- `docker ps/logs` / `docker exec ... psql -c "SELECT ..."`
- `curl` (테스트 데이터, 환자 PII X)
- 단위 테스트 (`cd back && uv run --project ai_server pytest ai_server/tests/ -m "not integration"`, `cd back && ./gradlew test`)

## 모호하면

명령이 read-only 인지 의심되면 **실행하지 마세요**. CTO 에게 보고만 하세요.

## 관련 룰

`.claude/rules/common/medical-safety.md`, `.claude/rules/common/clean-code.md`, `CLAUDE.md`
