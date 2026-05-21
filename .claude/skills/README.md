---
name: skills
description: PillMate 워크플로우 스킬 — TDD/DDD 사이클, RAG 평가, 처방전 OCR, 복약 검증 등
---

# PillMate Skills

PillMate 프로젝트에서 반복적으로 사용되는 워크플로우를 스킬로 정리한 모음입니다.
Claude Code의 `Skill` 도구로 호출됩니다.

## 스킬 카탈로그

| 스킬 | 목적 | 호출 키워드 |
|------|------|-------------|
| `tdd-cycle` | Red → Green → Refactor 강제 진행 | TDD, 테스트 우선 |
| `ddd-bootstrap` | 새 Bounded Context 골격 생성 | 새 도메인, bounded context |
| `ocr-recipe` | 처방전 OCR → 약 매칭 흐름 | OCR, 처방전 |
| `rag-eval` | RAG 정확도 평가 (RAGAS) | RAG 평가, faithfulness |
| `dose-check-flow` | 복용 체크 + 알림 흐름 검증 | 복용 체크 |
| `mfds-sync` | 식약처 API 동기화 | 식약처, MFDS |
| `cost-audit` | LLM/인프라 비용 감사 | 비용 감사 |
| `commit-convention` | 태그 기반 커밋 메시지 + 브랜치 전략 강제 | 커밋, 브랜치, push |

## 작성 규칙

- 스킬은 **재현 가능한 절차서**다 (체크리스트 형태)
- 각 단계마다 검증 조건 명시
- TDD/DDD 전제를 위반하는 절차 금지
