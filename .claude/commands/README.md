---
name: commands
description: PillMate 전용 슬래시 명령 모음 (/pill-*)
---

# PillMate Commands

PillMate 워크플로우를 즉시 호출하기 위한 슬래시 명령입니다.
Claude Code의 `/명령` 형태로 사용합니다.

## 명령 카탈로그

| 명령 | 설명 | 호출 스킬/에이전트 |
|------|------|-------------------|
| `/pill-tdd` | TDD 사이클 시작 | skills/tdd-cycle |
| `/pill-ddd-new` | 새 Bounded Context 생성 | skills/ddd-bootstrap |
| `/pill-ocr-test` | OCR 흐름 검증 | skills/ocr-recipe |
| `/pill-rag-eval` | RAG 정확도 평가 | skills/rag-eval |
| `/pill-mfds-sync` | 식약처 수동 동기화 | skills/mfds-sync |
| `/pill-cost` | 비용 감사 리포트 | skills/cost-audit |
| `/pill-arch-check` | DDD 레이어 의존 검증 | ArchUnit 실행 |

## 작성 규칙

- 각 명령은 frontmatter에 `name`, `description`, `target` 명시
- 명령은 사용자 입력 검증부터 시작
- 결과는 항상 다음 액션 제안으로 마무리
