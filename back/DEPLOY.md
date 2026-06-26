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

## 2. .env.prod 작성

```bash
mkdir -p /opt/pillmate/back/secrets
cp back/.env.prod.example /opt/pillmate/back/.env.prod
chmod 600 /opt/pillmate/back/.env.prod
nano /opt/pillmate/back/.env.prod   # 실 값 기입
```

**필수 항목** (빈값이면 기동 실패):

| 키 | 설명 |
|----|------|
| `DOMAIN` | 서버 도메인 (예: `api.pillmate.duckdns.org`) |
| `ACME_EMAIL` | Let's Encrypt 알림 이메일 |
| `POSTGRES_PASSWORD` | DB 강력한 패스워드 |
| `PILLMATE_JWT_SECRET` | 32자 이상 랜덤 문자열 |
| `KAKAO_REST_API_KEY` / `KAKAO_CLIENT_SECRET` | 카카오 개발자 콘솔 |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | S3 처방전 이미지 |
| `GEMINI_API_KEY` | Gemini AI |
| `OPENAI_API_KEY` | OpenAI 임베딩 |
| `SENTRY_DSN` / `SENTRY_DSN_AI` | Sentry 프로젝트 DSN |
| `SLACK_WEBHOOK_URL` | 운영 알림 웹훅 |

---

## 3. 도메인 설정 (무료)

### 옵션 A — DuckDNS

1. [duckdns.org](https://www.duckdns.org) 가입 → 서브도메인 생성 (예: `pillmate`)
2. 오라클 공인 IP → DuckDNS 레코드 설정
3. `.env.prod`: `DOMAIN=pillmate.duckdns.org`

### 옵션 B — nip.io (도메인 불필요, IP 기반)

```
DOMAIN=<오라클-공인-IP>.nip.io
```

> ⚠️ Let's Encrypt rate limit: 동일 도메인 주 5회. 테스트는 nip.io, 운영은 DuckDNS 권장.

---

## 4. Firebase 자격증명 (FCM 알림)

```bash
# 오라클 박스에서
mkdir -p /opt/pillmate/back/secrets
cp firebase-service-account.json /opt/pillmate/back/secrets/
chmod 600 /opt/pillmate/back/secrets/firebase-service-account.json
```

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
