---
name: agents
description: PillMate 도메인에 특화된 11개 서브에이전트 모음
---

# PillMate Agents

PillMate 프로젝트의 의료/AI/백엔드 도메인에 특화된 서브에이전트 13종입니다.
**TDD + DDD 레이어드 아키텍처**를 전제로 동작합니다.
Claude Code의 `Agent` 도구로 호출되며, 각 에이전트는 단일 책임을 가집니다.

## 에이전트 카탈로그

| 이름 | 역할 | 호출 시점 |
|------|------|-----------|
| `tdd-coach` | Red → Green → Refactor 사이클 강제 | 모든 도메인/유스케이스 코드 작성 시 |
| `ddd-modeler` | Bounded Context, Aggregate, ValueObject 설계 | 도메인 모델 정립/리뷰 |
| `spring-boot-architect` | Spring Boot 3 + DDD 레이어드 구조 | 백엔드 모듈 설계 |
| `fastapi-ai-engineer` | FastAPI + LangChain + Gemini | AI 서버 구현 |
| `prescription-ocr-expert` | 처방전 OCR + 약품명 매칭 | Gemini Vision 흐름 설계/디버깅 |
| `rag-curator` | pgvector + Hybrid Retrieval 관리 | RAG 인덱싱, 청크 전략 결정 |
| `medical-domain-validator` | 병용금기/용량/연령 금기 검증 | LLM 응답 사실성 검증 |
| `care-group-modeler` | N:N 케어 그룹 도메인 설계 | 권한/초대/QR 흐름 |
| `dose-schedule-engineer` | 복약 스케줄/체크 로직 | 월 900만 로그 배치 처리 |
| `drug-data-broker` | 식약처 API + Redis 캐싱 | 외부 API 한도 관리 |
| `s3-prescription-handler` | Pre-signed URL + 라이프사이클 | 처방전 이미지 보안/비용 |
| `msa-evolution-strategist` | Phase 2 → 3 점진 분리 전략 | 모놀리스 분리 시점 판단 |
| `cost-optimizer` | LLM 라우팅 + 캐싱 비용 최적화 | 모델 선택, 캐싱 전략 |

## 호출 예시

```
> /agent prescription-ocr-expert
> 처방전 OCR 정확도가 낮은데 Hybrid Retrieval 적용 방법을 알려줘.
```

## 작성 규칙

- 각 에이전트는 **단일 책임 원칙**을 따른다.
- frontmatter에 `name`, `description`, `tools`, `model`을 명시한다.
- 도메인 사실(식약처 API 스펙, Gemini 모델 가격 등)은 `contexts/` 에서 참조한다.
