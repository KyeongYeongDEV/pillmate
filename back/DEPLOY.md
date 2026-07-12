# PillMate 프로덕션 배포 가이드

> 오라클 A1 (arm64, 12GB/2OCPU) + GitHub Actions self-hosted runner + Caddy blue-green

---

## 1. 서버 초기 셋업

### 필수 패키지 설치

```bash
# Docker (arm64)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER && newgrp docker

# rsync (CI 동기화 용)
sudo apt-get install -y rsync curl git

# (테스트 게이트용) Java 17 + Python 3.11
sudo apt-get install -y openjdk-17-jdk python3.11 python3.11-venv python3-pip
```

### GitHub Actions Self-hosted Runner 등록

1. GitHub 저장소 → Settings → Actions → Runners → **New self-hosted runner**
2. OS: Linux, Architecture: ARM64
3. 화면의 지시에 따라 오라클 박스에 runner 설치:

```bash
mkdir ~/actions-runner && cd ~/actions-runner
# (GitHub이 제공하는 다운로드 URL 사용)
tar xzf actions-runner-linux-arm64-*.tar.gz
./config.sh --url https://github.com/<org>/<repo> --token <REGISTRATION_TOKEN>
```

4. 서비스 등록 (자동 시작):

```bash
sudo ./svc.sh install && sudo ./svc.sh start
```

---

## 2. .env.prod 작성 (자동 생성 — 서버에서 수동 편집 금지)

`.env.prod` 는 이제 **CI(`deploy.yml` "Generate .env.prod" 스텝)가 매 배포마다 GitHub Secrets/Variables 로부터 자동 생성**한다.
서버에서 `nano` 로 직접 만들면 다음 배포 때 덮어써지므로 **수동 작성 금지**. 대신 리포지토리에 아래 Secrets/Variables 를 1회 등록한다.

```bash
# 로컬(back/.env 보유 머신)에서 등록 계획 확인 (실제 등록 없음, 시크릿 값 화면 출력 없음)
bash back/scripts/gh-secrets-from-env.sh --dry-run

# 실제 등록 (로컬 .env 값 → GitHub Secrets/Variables)
bash back/scripts/gh-secrets-from-env.sh

# PILLMATE_JWT_SECRET 은 로컬 값 재사용 금지 — prod 전용 강한 값 신규 생성 등록
bash back/scripts/gh-secrets-from-env.sh --generate-jwt-secret
```

이 스크립트가 자동 도출 못 하는 항목(서버/도메인 정보)은 수동 등록:

```bash
gh variable set PROD_HOST --body pillmatefriend.duckdns.org
gh variable set ACME_EMAIL --body <실제 알림 이메일>
gh secret set POSTGRES_PASSWORD < <(openssl rand -base64 32)   # prod 전용 강한 값, 로컬 재사용 금지
```

**필수 GitHub Secrets** (빈값이면 CI 배포 스텝에서 컨테이너 기동 실패):
`POSTGRES_PASSWORD`, `PILLMATE_JWT_SECRET`, `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `GEMINI_API_KEY`, `OPENAI_API_KEY`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`. (선택: `SENTRY_DSN`, `SENTRY_DSN_AI`, `SLACK_WEBHOOK_URL`, `FIREBASE_CREDENTIALS_JSON_B64`)

**필수 GitHub Variables**: `PROD_HOST`(도메인), `ACME_EMAIL`. (선택, 기본값 있음: `AWS_REGION`, `S3_BUCKET_NAME`, `PILLMATE_NOTIFICATION_PROVIDER`, `DEPLOY_ROOT`)

키 분류/정확한 값 예시는 `back/.env.prod.example` 참고 (커밋된 값-없는 문서 — 서버 파일이 아님).

---

## 3. 도메인 설정 — DuckDNS (확정: `pillmatefriend.duckdns.org`)

1. [duckdns.org](https://www.duckdns.org) 서브도메인 `pillmatefriend` → 오라클 공인 IP(`161.33.7.211`) 레코드 등록 완료.
2. GitHub Variable: `PROD_HOST=pillmatefriend.duckdns.org` (Caddy `DOMAIN` 과 카카오 `KAKAO_REDIRECT_URI` 양쪽에 동일 값 사용 — 위 섹션 2 참고).
3. Caddy 가 이 도메인으로 Let's Encrypt 인증서를 **자동 발급/갱신**한다(`Caddyfile` 의 `{env.DOMAIN}` 사이트 블록 + `{env.ACME_EMAIL}` — 코드 변경 불필요, 이미 정합).

### ⚠️ 사전조건 — 오라클 방화벽 이중 오픈 필수

Let's Encrypt 발급(HTTP-01, 포트 80) + HTTPS(포트 443) 가 되려면 **아래 두 곳을 모두** 열어야 한다. 하나만 열면 외부 접속이 안 되는 오라클 클라우드 흔한 함정:

1. **오라클 클라우드 콘솔 → VCN → Security List** (또는 Network Security Group): TCP 80, 443 인바운드 `0.0.0.0/0` 허용.
2. **VM 내부 방화벽** (`iptables` 또는 Ubuntu `ufw`):
   ```bash
   sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
   # 영구 저장 (Ubuntu, netfilter-persistent 설치된 경우)
   sudo netfilter-persistent save
   ```
3. 확인: `curl -I http://pillmatefriend.duckdns.org` (배포 후) 응답 확인.

---

## 4. Firebase 자격증명 (FCM 알림, `PILLMATE_NOTIFICATION_PROVIDER=fcm` 일 때만)

`.env.prod` 와 마찬가지로 이제 CI가 자동 배치한다 — 서버 수동 `cp` 불필요:

```bash
# 1. GitHub Secret 등록 (서비스계정 JSON 을 base64 로)
gh secret set FIREBASE_CREDENTIALS_JSON_B64 < <(base64 -i firebase-service-account.json)
# 2. GitHub Variable 로 프로바이더 전환
gh variable set PILLMATE_NOTIFICATION_PROVIDER --body fcm
```

CI(`deploy.yml` "Write Firebase credentials" 스텝)가 `PILLMATE_NOTIFICATION_PROVIDER=fcm` 일 때만 decode 해
`/opt/pillmate/back/secrets/firebase-service-account.json` (권한 600)로 기록한다. `log`/`expo` 프로바이더면 이 스텝은 스킵.

---

## 5. 첫 배포

main 브랜치에 push 하면 GitHub Actions 가 자동 실행됩니다.

또는 수동:

```bash
cd ~/actions-runner/_work/pillmate/pillmate
bash back/scripts/deploy.sh
```

---

## 6. 배포 흐름

```
push main
  └─ GitHub Actions (self-hosted runner on 오라클 박스)
       ├─ ./gradlew test       (실패 시 배포 차단)
       ├─ pytest tests/ -m "not integration"
       ├─ Generate .env.prod   (GitHub Secrets/Variables → /opt/pillmate/back/.env.prod, 권한 600)
       ├─ Write Firebase credentials (fcm 일 때만)
       └─ bash back/scripts/deploy.sh
            ├─ workspace → /opt/pillmate/back rsync
            ├─ 반대 컬러(blue/green) 빌드 + 기동
            ├─ /actuator/health 200 대기 (타임아웃 120s)
            │   실패 → 새 컬러 폐기, 기존 유지 (안전)
            ├─ Caddyfile 업스트림 교체 + caddy reload (무중단)
            ├─ 기존 컬러 stop (컨테이너 유지 → 즉시 롤백 가능)
            └─ ai-server 롤링 재기동 (수초 블립)
```

---

## 7. 운영 명령

```bash
# 로그 확인
docker logs pillmate-app-blue -f
docker logs pillmate-caddy -f

# 즉시 롤백 (직전 컬러로)
bash /opt/pillmate/back/scripts/deploy.sh --rollback

# 서비스 상태
docker compose -f /opt/pillmate/back/docker-compose.yml \
               -f /opt/pillmate/back/docker-compose.prod.yml \
               --env-file /opt/pillmate/back/.env.prod \
               ps
```

---

## 8. ARM64 주의사항

> **배포 실측 확인 항목**: 오라클 A1(arm64)에서 첫 빌드 시 `torch` / `FlagEmbedding` arm64 패키지 설치를 실측 확인해야 합니다.

- `torch`: arm64 wheel 이 PyPI에 없으면 빌드 시간이 길어질 수 있음 (10~20분)
- `FlagEmbedding`: `transformers`에 의존 → arm64 지원 여부 확인
- 문제 시: `ai_server/Dockerfile`에서 arm64 대안 wheel 또는 CPU-only torch 사용

```dockerfile
# arm64 CPU-only torch (필요 시)
RUN pip install torch --index-url https://download.pytorch.org/whl/cpu
```

### Caddy rate_limit 모듈 (xcaddy, arm64)

- 표준 `caddy:2-alpine` 엔 `rate_limit` 모듈이 없어 `caddy/Dockerfile` 이 `xcaddy build --with github.com/mholt/caddy-ratelimit` 로 커스텀 이미지를 빌드합니다.
- 오라클 A1(arm64)에서 빌드되므로 arm64 바이너리가 자동 생성됩니다 (별도 크로스컴파일 불필요).
- 첫 빌드는 Go 툴체인 + 모듈 컴파일로 1~3분 소요. 이후 레이어 캐시.
- 빌드 후 확인: `docker run --rm <image> caddy list-modules | grep rate_limit` → `http.handlers.rate_limit` 노출되면 정상.

---

## 9. 메모리 한도 (12GB 기준)

| 서비스 | mem_limit | 비고 |
|--------|-----------|------|
| app-blue | 2g | 한 번에 하나만 full 기동 |
| app-green | 2g | 전환 순간만 2벌 동시 (총 4g) |
| ai-server | 3g | 롤링(1벌만) |
| postgres | 2g | |
| redis | 512m | |
| caddy | 256m | |
| **총 (정상)** | **~7.7g** | |
| **총 (전환 피크)** | **~9.8g** | 12g 이내 안전 |

---

## 10. Swap 설정 (OOM 보험)

배포 전환 피크 메모리가 ~10-11g/12g 에 근접하므로, 물리램만으로는 순간 스파이크에 OOM-kill 위험이 있습니다. swapfile 로 완충합니다.

```bash
# 서버에서 1회 실행 (8G swap + swappiness=10)
cd back/scripts
sudo ./setup-swap.sh          # 또는 sudo ./setup-swap.sh 4G
```

- `vm.swappiness=10`: **물리램을 우선 사용**하고 swap 은 스파이크 완충으로만 쓰도록 낮게 설정. (기본 60 은 평상시에도 swap 을 적극 사용해 지연 증가)
- `/etc/fstab` 등록으로 재부팅 후에도 유지.
- swap 은 어디까지나 **OOM 방지 보험** — 상시 swap 사용이 관측되면(예: `free -h` 에서 used swap 지속 증가) mem_limit 조정 또는 스케일업을 검토합니다.

---

## 11. 마이그레이션 안전 (blue-green)

> **핵심 제약**: blue-green 은 **Postgres 1대를 blue/green 이 공유**합니다. green 기동 시 Flyway 가 공유 스키마를 변경하는데, 이 변경이 하위호환되지 않으면 **아직 트래픽을 받는 구버전(blue) 코드가 깨집니다.**

### 규칙 — Expand-Contract (배포 중 additive-only)

새 마이그레이션은 "직전 컬러(구버전 코드)와 **동시에** 돌아도 안전한가?" 를 반드시 통과해야 합니다.

| 변경 | 허용 여부 | 방법 |
|------|-----------|------|
| 컬럼/테이블 추가 | ✅ 즉시 | additive — 구버전은 새 컬럼을 모르고도 정상 동작 |
| 컬럼 `NOT NULL` 추가 | ⚠️ 조건부 | `DEFAULT` 동반 또는 backfill 완료 후 별도 배포 |
| 컬럼/테이블 **DROP** | ❌ 2단계 분리 | ① 코드에서 사용 중단 배포 → ② **다음 배포**에서 제거 |
| 컬럼/테이블 **RENAME** | ❌ 2단계 분리 | ① 새 컬럼 추가 + dual-write → ② 구 컬럼 제거 (RENAME = DROP+ADD 로 취급) |

### db-safety.md 정합

- `DROP`/`DELETE`/`TRUNCATE` 계열은 [.claude/rules/common/db-safety.md](../.claude/rules/common/db-safety.md) 의 **P0 절대 금지** 대상입니다.
- 본 섹션의 "2단계 분리"는 그 우회가 **아닙니다** — DROP 을 수행하는 별도 배포 자체가 **사용자 명시 동의 + spec 명시**를 요구합니다. expand-contract 는 "언제 안전한지"의 절차일 뿐, 삭제 허용 근거가 아닙니다.

### 배포 전 체크리스트

- [ ] 이 마이그레이션이 구버전 코드와 동시에 돌아도 안전한가 (additive-only)?
- [ ] DROP/RENAME 이 포함되면 → 2단계로 분리했는가? 사용자 동의를 받았는가?
- [ ] `NOT NULL` 추가 시 default/backfill 이 있는가?

---

## 12. 무한요청 방어 (2계층)

Oracle A1 12GB **단일 박스**라 볼류메트릭 flood 에 취약 → 엣지(IP)와 앱(user) 두 계층으로 방어합니다.

### 계층 ① — Caddy 엣지 per-IP (`Caddyfile` + `caddy/Dockerfile`)

- `rate_limit` 모듈(mholt/caddy-ratelimit)을 xcaddy 커스텀 이미지로 포함 (§8 참조).
- `zone per_ip`: 전 경로 IP당 **40r/s** (정상 사용자 여유, flood 차단).
- `zone auth_ip`: `/api/v1/auth/*` IP당 **5r/s** (크리덴셜 brute-force 방어).
- 초과 시 Caddy 가 **429** 반환 — 앱까지 도달하지 않아 서버 부하 0.

> ⚠️ **회귀 주의**: `deploy.sh` 의 `write_caddyfile()` 이 매 배포마다 `Caddyfile` 을 재작성합니다.
> 이 함수 템플릿에 `rate_limit` 블록이 포함돼 있어야 하며(현재 포함됨), 누락 시 계층① 방어가 조용히 사라집니다.
> 배포 후 확인: `docker exec pillmate-caddy cat /etc/caddy/Caddyfile | grep rate_limit`.

### 계층 ② — 앱 전역 per-user (`GlobalRateLimitInterceptor`)

- 모든 **인증** 요청에 대해 `rl:req:{userId}:{yyyyMMddHHmm}` 분당 카운터 (Redis, TTL 60s).
- 한도: `PILLMATE_GLOBAL_RPM` (default **120**/분). 정상 폴링(30초=분당 2~4) 대비 넉넉.
- 초과 시 **429** (`PILL_090`, "오늘 사용량을 초과했어요"). `/auth`·`/actuator` 는 제외(계층①이 담당).
- **fail-open**: Redis 장애 시 요청을 **통과**시키고 warn 로그만 남깁니다 (rate limiter 가 서비스 전체를 막으면 안 됨 — 가용성 우선).

### 기존 방어와의 관계

- OCR 일일 한도(50/일), 리포트 새로고침(1/일)은 **별도 도메인 한도**로 그대로 유지 — 2계층과 중첩 방어.
- 튜닝: 정상 사용자가 429 를 받으면 `PILLMATE_GLOBAL_RPM` 상향 또는 Caddy `events` 조정. 상시 429 관측 시 로그로 공격/오탐 구분.

## 13. DB 백업 (일 1회 cron)

의료 데이터는 복구 비용이 코드보다 비싸다(`.claude/rules/common/db-safety.md`). 백업 없이 운영 금지.

### 배선 (서버 1회)

```bash
# 매일 04:00 KST pg_dump 백업 cron 등록 (멱등 — 중복 등록 안 함)
bash /opt/pillmate/back/scripts/setup-backup-cron.sh
crontab -l | grep pillmate-db-backup   # 확인
```

- `backup_postgres.sh`: `docker exec pillmate-postgres pg_dump --format=custom`(read-only) → `back/.backups/postgres-YYYY-MM-DD-HHMM.dump.gz`, `chmod 600`, **7일 보관**(오래된 백업 파일만 삭제 — DB 아님).
- **db-safety 준수**: pg_dump 는 read-only. DELETE/DROP/TRUNCATE 없음.

### S3 오프사이트 (VM 필수 — 로컬/VM 유실 대비)

VM 컨테이너 postgres 는 VM 유실 시 백업도 함께 소멸 → **S3 오프사이트 필수**(의료 데이터).

- `backup_postgres.sh` 는 `BACKUP_S3=true` 일 때 로컬 덤프 후 `s3://${S3_BUCKET_NAME}/db-backups/YYYY/MM/` 로 업로드(`--sse AES256`). 실패해도 로컬 백업 유지(fail-soft) + `exit 1` 로 cron 로그에 남김.
- **cron 배선(위 `setup-backup-cron.sh`)이 `BACKUP_S3=true` 를 포함** — 스크립트가 `/opt/pillmate/back/.env.prod` 에서 `S3_BUCKET_NAME`/AWS 키를 로드(시크릿은 크론라인 미포함).
- 로컬(Mac)은 `BACKUP_S3` 기본 `false` — dev 덤프를 prod 버킷에 올리는 실수 방지.
- **aws cli 미설치 환경**: 스크립트가 `docker run --rm amazon/aws-cli` 로 폴백(크리덴셜 env 주입, argv 노출 0).

**라이프사이클(30일 자동 만료 = 비용 절감)** — `db-backups/` prefix 만:

```bash
# --dry-run(기본): 적용될 JSON 출력만. 실적용은 --apply (CTO/사용자 승인 후)
S3_BUCKET_NAME=<bucket> bash /opt/pillmate/back/scripts/setup-s3-backup-lifecycle.sh
S3_BUCKET_NAME=<bucket> bash /opt/pillmate/back/scripts/setup-s3-backup-lifecycle.sh --apply
```

> ⚠️ `put-bucket-lifecycle-configuration` 은 **전체 교체**다. 스크립트는 기존 규칙을 `get` 후 병합해 `db-backups/` 규칙만 추가한다. 처방전 이미지 prefix 는 규칙 없음(영구 보관 = 테스트 코퍼스 의도) — **이미지용 규칙을 추가하지 말 것**.

### 복원 (수동, 사고 시 — db-safety 명시 동의 전용)

```bash
# 1) 로컬 백업 확인
bash /opt/pillmate/back/scripts/verify_backup.sh
# 1-b) 로컬 유실 시 S3 에서 최신 백업 다운로드 (aws cli 없으면 docker amazon/aws-cli)
#   aws s3 cp s3://${S3_BUCKET_NAME}/db-backups/<YYYY>/<MM>/postgres-<TS>.dump.gz back/.backups/
# 2) 복원 (custom 포맷) — 실행 전 대상 DB/시점 재확인 필수
gunzip -c back/.backups/postgres-<TS>.dump.gz > /tmp/restore.bin
docker cp /tmp/restore.bin pillmate-postgres:/tmp/restore.bin
docker exec pillmate-postgres pg_restore -U pillmate -d pillmate --clean --if-exists /tmp/restore.bin
```

> 🚨 **`pg_restore --clean` 은 기존 객체를 DROP 후 복원 = 데이터 파괴적**. `db-safety.md` P0 규칙에 따라 **복구 시나리오 전용 + 사용자/CTO 명시 동의 후에만** 실행. 자동화 절대 금지 — 운영자가 시점/대상 확인 후 수동 실행.
