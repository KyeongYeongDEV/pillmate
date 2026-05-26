# PillMate

monorepo: BE (Spring Boot + FastAPI) + FE (React Native + Expo)

## 구조

```
pillmate/
├── back/
│   ├── app_server/    # Spring Boot 3 (src/, build.gradle, gradlew, Dockerfile)
│   ├── ai_server/     # FastAPI + LangChain (OCR / RAG / 추천)
│   ├── infra/         # postgres init.sql
│   ├── scripts/       # ETL (식약처 bulk import, 임베딩)
│   └── docker-compose.yml
└── front/             # React Native + Expo 크로스플랫폼
```

## Quick Start

```bash
# Backend
cd back && docker compose up -d

# Spring Boot 테스트
cd back/app_server && ./gradlew test

# AI 서버 테스트
cd back/ai_server && ../.venv/bin/python -m pytest tests/ -m "not integration"

# Frontend
cd front && npm install && npx expo start
```

## 개발 명령

| 목적 | 명령 |
|------|------|
| Spring 빌드 | `cd back/app_server && ./gradlew clean build -x test` |
| Spring 테스트 | `cd back/app_server && ./gradlew test` |
| AI 서버 테스트 | `cd back/ai_server && ../.venv/bin/python -m pytest tests/ -m "not integration"` |
| 컨테이너 기동 | `cd back && docker compose up -d` |
| 컨테이너 재빌드 | `cd back && docker compose build --no-cache` |
| 컨테이너 상태 | `docker ps` |
| FE 개발 서버 | `cd front && npx expo start` |
| FE 테스트 | `cd front && npm test` |
| FE 타입 검사 | `cd front && npx tsc --noEmit` |
