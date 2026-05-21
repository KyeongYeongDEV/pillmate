---
name: contexts
description: 동적 시스템 프롬프트 — 도메인, 용어, 진화 스토리
---

# PillMate Contexts

특정 작업 모드에서 LLM에 주입되는 컨텍스트 문서입니다.

## 컨텍스트 목록

| 컨텍스트 | 사용 시점 |
|----------|-----------|
| `medical-domain.md` | 의료 정확성 검증, RAG 응답 |
| `ubiquitous-language.md` | 코드 작성, 리뷰, 문서화 |
| `evolution-story.md` | 아키텍처 결정 회고, 면접 준비 |

## 사용 방법

```
[ai 응답 전]
시스템 프롬프트에 contexts/medical-domain.md 내용 주입
→ "출처 명시" 등 규칙 강제
```
