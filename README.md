# PillMate

monorepo: BE (Spring Boot + FastAPI) + FE (React Native + Expo)

## 구조

```
pillmate/
├── back/    # 서버 (Spring Boot + FastAPI + Docker)
└── front/   # 모바일 앱 (React Native + Expo)
```

## Quick Start

```bash
# Backend
cd back && docker compose up -d
cd back && ./gradlew test

# Frontend
cd front && npm install && npx expo start
```

## 개발 명령

| 목적 | 명령 |
|------|------|
| Spring 빌드 | `cd back && ./gradlew clean build -x test` |
| Spring 테스트 | `cd back && ./gradlew test` |
| AI 서버 테스트 | `cd back && uv run --project ai_server pytest ai_server/tests/ -m "not integration"` |
| 컨테이너 기동 | `cd back && docker compose up -d` |
| 컨테이너 상태 | `docker ps` |
| FE 개발 서버 | `cd front && npx expo start` |
| FE 테스트 | `cd front && npm test` |
| FE 타입 검사 | `cd front && npx tsc --noEmit` |
