---
name: rules
description: PillMate 코딩 규칙 모음 (공통 + 언어별 + DB)
---

# PillMate Rules

PillMate에서 강제되는 코딩 규칙입니다. 모든 PR은 이 규칙을 통과해야 합니다.

## 구성

```
rules/
├── common/             # 언어 무관 공통 규칙
│   ├── tdd-cycle.md
│   ├── no-overengineering.md
│   ├── cost-aware.md
│   └── medical-safety.md
├── java/               # Spring Boot 백엔드
│   ├── spring-boot.md
│   ├── jpa.md
│   ├── junit.md
│   └── ddd-layered.md
├── python/             # FastAPI AI 서버
│   ├── fastapi.md
│   └── langchain.md
└── sql/                # PostgreSQL
    └── postgres.md
```

## 우선순위

위반 시 다음 순서로 차단:
1. **medical-safety** — 환자 안전 (즉시 차단)
2. **ddd-layered** — 아키텍처 무결성 (PR 차단)
3. **tdd-cycle** — 테스트 우선 (PR 차단)
4. 나머지 — 리뷰 코멘트
