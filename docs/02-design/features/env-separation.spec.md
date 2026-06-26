# Spec — 로컬 ↔ 배포 환경 분리 (docker compose)

> 목적: 곧 배포. 로컬(dev 편의·약한 시크릿)과 프로덕션(fail-closed·실 시크릿·포트 비노출)을 명확히 분리.
> 원칙: ★실 시크릿은 git 금지(.env.prod gitignore), 보안 가드(dev-fallback off·JWT 필수·X-User-Id off)는 prod에서 자동 닫힘. 오버엔지니어링 회피(필요한 것만).

## 패턴 — Docker override
- `back/docker-compose.yml` = ★base + 공통(서비스/build/네트워크/볼륨/healthcheck). 로컬 전용 값은 여기서 빼서 override로.
- `back/docker-compose.override.yml` = ★로컬 전용(자동 로드). dev 편의:
  - app/ai-server env: `PILLMATE_DEV_FALLBACK=true`, `PILLMATE_JWT_SECRET=dev-local-only-...`, `SPRING_PROFILES_ACTIVE=local`
  - postgres/redis 포트 publish(5433:5432, 6379:6379) — 로컬 디버깅용
  - (현재 docker-compose.yml의 로컬 전용 값들을 이리로 이동)
- `back/docker-compose.prod.yml` = ★프로덕션(명시 지정). 오버라이드:
  - `SPRING_PROFILES_ACTIVE=production`
  - app/ai-server env_file: `.env.prod` (실 시크릿)
  - ★`PILLMATE_DEV_FALLBACK` 미설정(=false, fail-closed) — dev fallback·X-User-Id 폴백 OFF
  - postgres/redis ★포트 publish 안 함(내부 네트워크만)
  - `restart: unless-stopped`, `mem_limit`(app 4g/ai 3g/pg 2g/redis 512m 기준 조정 — 오라클 12GB)
  - (선택) caddy 서비스(자동 HTTPS, 443/80) — 도메인 생기면. 지금은 주석/후속 가능.

## 실행
- 로컬: `docker compose up -d`  (base + override.yml 자동)
- 프로덕션: `docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build`

## .env.prod
- `back/.env.prod.example` (★committed, placeholder) — 필요한 키 목록 문서화:
  ```
  SPRING_PROFILES_ACTIVE=production
  POSTGRES_PASSWORD=<strong>
  PILLMATE_JWT_SECRET=<strong 32+ chars>
  KAKAO_REST_API_KEY=<kakao>
  KAKAO_CLIENT_SECRET=<kakao>
  SENTRY_DSN=<spring-app DSN>       # ai-server는 자기 것
  SENTRY_DSN_AI=<fastapi-ai DSN>
  SLACK_WEBHOOK_URL=<#sentry-failed webhook>
  AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / S3 bucket
  GOOGLE/GEMINI_API_KEY, OPENAI_API_KEY
  AI_SERVER_BASE_URL=http://ai-server:8001
  ```
- `back/.env.prod` = ★gitignore(.env.* 이미 ignore). 실제 값은 CTO가 monitor/*/.env 등에서 모아 서버에 둠.

## 검증/안전
- ★prod 프로필에서 PILLMATE_JWT_SECRET 미설정/기본값이면 기동 실패(이미 ProductionSecurityValidator) — 그대로 동작 확인.
- prod compose로 띄우면 dev-fallback OFF (빈 code 로그인 → 401), X-User-Id 폴백 OFF.
- DB/Redis 포트 미노출(보안).
- 기존 로컬 `docker compose up` 동작 회귀 0 (override 자동 로드).
- ARM: 오라클 A1(arm64)에서 build 시 torch/FlagEmbedding 등 arm64 설치 확인은 배포 실측 항목.
- db-safety: 어떤 compose도 DROP/DELETE 없음.

## 산출물
- docker-compose.override.yml, docker-compose.prod.yml, .env.prod.example, (README 실행법)
- 기존 docker-compose.yml에서 로컬 전용 값 override로 이동(회귀 주의)
