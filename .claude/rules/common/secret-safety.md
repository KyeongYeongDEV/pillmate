# Secret Safety — 시크릿 취급 절대 규칙

> **P0 규칙 (2026-07-07 제정, db-safety 와 동급)**. 근거 사고: 2026-07-06 BE-Dev 가 CICD 검증 중
> `docker compose config` 를 `--env-file` 없이 실행 → compose 가 기본 `back/.env`(실제 키)를 자동 로드
> → GEMINI/OPENAI 실키가 세션 로그에 노출. 외부 유출은 아니나 로테이션 필요해짐(배포 전 로테이션 결정).

## 절대 금지 (모든 에이전트)

| 금지 | 이유 / 대체 |
|------|------------|
| 시크릿 값을 채팅·보고·로그·커밋에 출력 | 마스킹 필수: `sed 's/=\(.\{4\}\).*/=\1***/'` |
| `docker compose config` 를 `--env-file` 명시 없이 실행 | 기본 `.env`(실키) 자동 로드됨 → **반드시 더미 env-file 명시** |
| `printenv` / `env` 결과를 필터 없이 출력 | `grep KEY | sed마스킹` 만 |
| 시크릿을 명령줄 인자로 전달 (`--secret=값`) | 프로세스 목록/히스토리 노출 → env 주입 or stdin 스트림 |
| `.env*` 파일 내용 전체 cat | 키 이름만 필요하면 `cut -d= -f1` |
| 실제 키를 예시/문서/spec 에 복붙 | placeholder (`<key>`) 사용 |
| 시크릿 값을 git 에 커밋 (`.env`, 인증서, 서비스계정 JSON) | `.gitignore` 확인 + GitHub Secrets 사용 |
| 스크린샷 속 토큰/키를 문서·메모리에 옮겨 적기 | 위치만 기록 ("콘솔 X 페이지에 있음") |

## 의무 절차

1. **노출 발생 시**: 즉시 작업 중단 → CTO 보고 → 노출 범위 기록(로컬 로그 only / 외부) → **로테이션 태스크 등록** (deploy-gate 체크리스트에 자동 포함).
2. **검증 스크립트**: compose 검증은 항상 `--env-file <SENTINEL 더미>` 로. 더미 값은 `SENTINEL_XXX` 접두로 실키와 구분.
3. **GitHub Secrets 등록**: `gh secret set NAME < file` or 프로세스 치환 — echo 로 값 노출 금지.
4. **파일 권한**: 서버의 `.env.prod`, 서비스계정 JSON 은 600 (`umask 077`).
5. **전용 위치**: 시크릿 파일은 `back/.env`(로컬), `/opt/pillmate/back/.env.prod`(서버), GitHub Secrets — 그 외 사본 금지 (scratchpad 포함).

## 현재 미결 (deploy-gate 연동)
- [ ] GEMINI_API_KEY 로테이션 (2026-07-06 노출건) — **배포 전 필수**
- [ ] OPENAI_API_KEY 로테이션 (동일)
- [ ] PILLMATE_JWT_SECRET prod 강한 값 신규 생성 (`openssl rand -base64 48`)

## 참조
- `.claude/rules/common/db-safety.md` — 동급 P0 선례
- `.claude/skills/deploy-gate.md` — 배포 전 로테이션 게이트
