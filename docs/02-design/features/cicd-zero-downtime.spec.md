# Spec — CI/CD + 무중단 배포 (GitHub Actions + Caddy blue-green)

> dev 개발 → main 머지 → 오라클 A1(arm64, 12GB/2OCPU) 자동 배포.
> 무중단: ★app만 blue-green(프록시 전환), ai-server는 롤링(수초 블립, 내부용). 12GB라 풀 blue-green(2벌×전체)=OOM 회피.
> 환경분리(docker-compose.override/prod) 위에 얹음. 시크릿은 서버 .env.prod만(git/Actions 금지).

## 구성요소
1. **Caddy 리버스 프록시** (prod compose 서비스, 443/80, 자동 HTTPS)
   - `monitor/`가 아니라 `back/`에 `Caddyfile` + prod compose의 caddy 서비스.
   - reverse_proxy → 활성 app 컬러(app-blue 또는 app-green). 도메인 env(`DOMAIN`).
2. **app blue/green** (prod compose): app-blue, app-green 두 서비스(같은 이미지, 다른 컨테이너명). 평상시 한쪽만 up.
3. **scripts/deploy.sh** (서버에서 실행, 멱등):
   - 현재 활성 컬러 판별 → 반대 컬러로 새 이미지 빌드·기동 → `/actuator/health` 200 대기(타임아웃) → Caddy 업스트림 새 컬러로 교체 + `caddy reload`(무중단) → 옛 컬러 stop → ai-server 롤링 재기동.
   - 실패 시(헬스 미달) 전환 안 하고 새 컬러 폐기 → 기존 유지(안전).
4. **.github/workflows/deploy.yml**:
   - trigger: push to `main`
   - ★self-hosted runner(오라클 박스, arm64 네이티브 빌드 — buildx 불필요)
   - step: checkout → `bash scripts/deploy.sh` (git pull은 runner가 이미 체크아웃)
   - (테스트 게이트: 빌드 전 `./gradlew test` + ai pytest 통과해야 배포 — 선택)

## 시크릿/환경
- 서버 `back/.env.prod`(chmod 600, gitignore) — deploy.sh가 prod compose에 `--env-file` 로 주입.
- GitHub엔 시크릿 0(self-hosted runner라 SSH·DSN 불필요). 단 self-hosted runner 등록 토큰만 사용자가 1회.

## 무중단 동작 (app)
```
평상시: Caddy → app-blue(v1)
배포:   app-green(v2) 기동 → health 200 대기(blue 계속 서비스)
        → Caddy 업스트림 green 전환 + reload (끊김 0)
        → app-blue stop (롤백 대비 잠깐 유지 옵션)
        → ai-server 롤링 재기동(수초)
```

## 안전/제약
- ★12GB: 전환 순간 app 2벌(~4GB)만. ai-server 2벌 금지(롤링).
- prod fail-closed 유지(JWT 미설정 기동실패·dev-fallback off).
- DB/Redis 포트 비노출(prod compose 그대로).
- db-safety: 배포 스크립트에 DROP/DELETE 없음. 마이그레이션(Flyway)은 app 기동 시 자동(additive만).
- ARM: A1 네이티브 빌드라 torch/FlagEmbedding arm64 설치 — 첫 배포 실측 확인 항목.
- 롤백: Caddy를 옛 컬러로 되돌리고 reload(옛 컨테이너 유지 시 즉시).

## 산출물
- back/Caddyfile, prod compose에 caddy + app-blue/app-green
- scripts/deploy.sh (health-gated 전환, 멱등, 실패 시 안전)
- .github/workflows/deploy.yml (main push → self-hosted runner → deploy.sh)
- README: self-hosted runner 등록법 + .env.prod 작성 + 도메인 설정(무료 DuckDNS/nip.io) 안내

## 사용자(대표) 액션 (구현과 별개, 배포 시)
- 오라클 박스에 self-hosted runner 등록, .env.prod 작성, 도메인 연결, 카카오 키.
