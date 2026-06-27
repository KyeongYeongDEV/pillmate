# T-DOCKER-BUILD-OPT — Docker 빌드 최적화 (레이어 분리 + BuildKit cache + 멀티스테이지)

작성일: 2026-06-27
사용자 명시: "도커 빌드 최적화하는 방법" → 적용 디스패치

## 배경

`docker compose build app ai-server`가 매번 의존성 전체 다운로드(Gradle/pip)로 5~10분 소요. 운영/CI에 부담 + 개발 반복 효율 저하.

## 목표

- **첫 빌드 시간 동일** (-/+ 10% 이내)
- **코드 1줄 변경 후 재빌드 시간 50% 이상 감소** (목표: 30초~1분)
- 이미지 크기 감소 (가능 시)
- DB/runtime 동작 무영향 (인프라 변경만)

## 절대 규칙 (재확인)

- DB 데이터 삭제 금지 (DELETE/TRUNCATE/DROP) — 본 task는 DB 무관
- git commit/push 금지 (CTO 단독) — 워킹트리만, DONE 보고
- clean-code: Dockerfile 단계별 주석 한 줄 허용 (외부 의도)
- no-overengineering: 이미 검증된 기법만, 신규 도구 도입 없음

---

## BE-Dev 작업

### 1. 영향 파일

- `back/app_server/Dockerfile` (수정)
- `back/ai_server/Dockerfile` (수정)
- `back/docker-compose.yml` (수정 — build 섹션에 args/cache_from 추가 검토)
- `back/.dockerignore` (신규 또는 수정)
- `back/app_server/.dockerignore` (신규 — 컨텍스트별)
- `back/ai_server/.dockerignore` (신규)

### 2. 적용 기법 (효과 순)

#### 2.1 레이어 분리 — 의존성 먼저, 소스 마지막

**app_server (Gradle)**:
```dockerfile
# syntax=docker/dockerfile:1.6
FROM gradle:8-jdk17-alpine AS builder
WORKDIR /app
COPY build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle gradle dependencies --no-daemon || true
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle gradle bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

- 의존성 레이어가 코드 변경에 무관해짐 → 재빌드 시 cache hit
- `gradle dependencies` 실패해도 진행 (`|| true`) — 일부 환경 호환

**ai_server (pip)**:
```dockerfile
# syntax=docker/dockerfile:1.6
FROM python:3.11-slim AS builder
WORKDIR /app
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip pip install --no-warn-script-location --user -r requirements.txt
COPY app ./app

FROM python:3.11-slim
WORKDIR /app
COPY --from=builder /root/.local /root/.local
COPY --from=builder /app/app ./app
ENV PATH=/root/.local/bin:$PATH
EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
```

- builder에서 `--user` 설치 → runtime에 site-packages만 복사
- 캐시 마운트로 pip wheel 재사용

#### 2.2 BuildKit cache mount

- `RUN --mount=type=cache,target=...` 위 예시 적용
- 환경: `export DOCKER_BUILDKIT=1` (Docker Desktop 기본). compose v2.20+ 기본 활성
- `# syntax=docker/dockerfile:1.6` 첫 줄 필수

#### 2.3 멀티스테이지 빌드

- 위 예시는 이미 builder/runtime 분리
- runtime 이미지: `eclipse-temurin:17-jre-alpine` (JDK 아닌 JRE), `python:3.11-slim`

#### 2.4 .dockerignore

신규 또는 갱신:

```
# .dockerignore (back/ 루트, 각 서비스 컨텍스트별 별도)
.git
.gitignore
.gradle
build
out
.idea
.vscode
*.log
*.tmp
__pycache__
*.pyc
.venv
venv
.pytest_cache
.mypy_cache
tests
test
docs
*.md
```

- 컨텍스트가 작아질수록 build 시작 시간 줄어듦
- 보안: secrets/firebase-service-account.json 등 민감 파일은 그대로 mount 유지 (compose.yml의 volumes)

#### 2.5 docker compose parallel build

- 빌드 명령: `docker compose build --parallel app ai-server`
- compose.yml 변경 불필요 (CLI 옵션)

#### 2.6 (선택) compose.yml에 build args + cache_from

```yaml
services:
  app:
    build:
      context: ./app_server
      dockerfile: Dockerfile
      cache_from:
        - pillmate-app:latest
    image: pillmate-app:latest
```

- 로컬 캐시 재사용 명시. CI에서는 `cache_from`/`cache_to` registry 활용 가능 (Phase 2)

### 3. 검증 (인수 기준)

**측정 시나리오**:

1. **첫 빌드** (cache 없음):
   ```
   docker compose build --no-cache app ai-server
   time 측정
   ```

2. **코드 1줄 변경 후 재빌드**:
   ```
   echo "// dummy" >> back/app_server/src/main/java/com/pillmate/.../PrescriptionController.java
   time docker compose build app ai-server
   ```
   → 의존성 레이어 cache hit, 소스 레이어만 재빌드 → 30초~1분 기대

3. **의존성 1개 추가 후 재빌드**:
   ```
   echo "" >> back/app_server/build.gradle
   time docker compose build app
   ```
   → 의존성 레이어 재빌드, cache mount로 다운로드 일부 재사용

**인수**:
- 시나리오 2 시간 ≤ 첫 빌드 시간의 30% (50% 감소 달성)
- 시나리오 1 시간 ≤ 첫 빌드 + 10% (최적화 후 첫 빌드 동등 또는 약간 증가 허용 — 멀티스테이지 오버헤드 감안)
- `docker images pillmate-app pillmate-ai-server` 크기 비교 — 동등 또는 감소
- `docker compose up -d` 후 healthcheck 통과 + 기존 endpoint smoke 통과 (GET /prescriptions/latest-with-insight, POST /api/v1/analyze/health-report 등)

### 4. 위험 / rollback

- Dockerfile 변경 후 빌드 실패 시: 워킹트리에 변경만 있으므로 `git checkout Dockerfile` 으로 즉시 rollback
- runtime 이미지 변경(alpine)으로 native library 호환 문제 발생 시: `eclipse-temurin:17-jre-jammy` 또는 `python:3.11-slim` 으로 fallback
- 데이터 무영향: DB volume, 코드 mount 변경 없음

### 5. 비-범위 (out of scope)

- CI/CD registry cache (GHA `cache-from=type=gha`) — Phase 2
- Distroless image — 검증 부담, 호환 이슈 → 별도 검토
- Bake / `docker buildx bake` 도입 — 현 빌드 흐름 유지
- ARM/x86 멀티 플랫폼 — 출시 단계 검토

### 6. 보고 형식

작업 완료 시: `.cmux/messages/cto/inbox/T-DOCKER-BUILD-OPT-be-done.json`

포함:
- 변경 파일 목록
- 시나리오 1/2/3 시간 측정값 (초)
- 이미지 크기 변화
- `docker compose up -d` 후 healthcheck 결과
- smoke endpoint 결과 (200 OK / payload 일부)
- git status (워킹트리만)
