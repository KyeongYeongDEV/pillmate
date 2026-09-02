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
│   ├── medical-safety.md
│   ├── db-safety.md         ★ 2026-05-25 신규 — 모든 에이전트 DB 삭제 금지
│   ├── secret-safety.md     ★ 2026-07-07 신규 — 시크릿 노출 금지 (P0)
│   ├── verification-evidence.md ★ 2026-07-07 신규 — DONE 은 실측 증거 의무
│   ├── qa-risk-tiers.md     ★ 2026-07-07 신규 — 리스크 기반 2단계 QA
│   ├── context-hygiene.md   ★ 2026-07-07 신규 — compact/clear + 서브에이전트 위임(2026-09-02 단일세션)
│   ├── scope-discipline.md  ★ 2026-07-07 신규 — 무관코드·anti-revival·동시편집 금지
│   └── clean-code.md
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
2. **db-safety** — DB 데이터 보호 (즉시 차단) ★ 2026-05-25 추가
3. **secret-safety** — 시크릿 노출 (즉시 차단) ★ 2026-07-07 추가
4. **ddd-layered** — 아키텍처 무결성 (PR 차단)
5. **tdd-cycle** — 테스트 우선 (PR 차단)
6. **verification-evidence** — 실측 증거 없는 DONE 반려 ★ 2026-07-07 추가
7. 나머지 — 리뷰 코멘트
