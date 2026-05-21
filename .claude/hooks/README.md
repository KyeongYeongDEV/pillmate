---
name: hooks
description: PillMate 자동화 훅 정의
---

# PillMate Hooks

`hooks.json`에 정의된 자동화 훅으로 다음을 강제합니다.

## 훅 목록

| 훅 | 트리거 | 작동 |
|----|--------|------|
| TDD Pair Check | `domain/**/*.java` 작성 | 대응 테스트 파일 존재 여부 확인 |
| Layer Dependency Check | 모든 `.java` 작성 | DDD 레이어 의존 방향 위반 검출 |
| Medical Source Check | AI 서버 `.py` 작성 | 식약처 출처 강제 패턴 검증 |
| Test Run Indicator | `./gradlew test` 실행 | TDD 사이클 표시 |
| Session Summary | 세션 종료 | TDD 위반 + 비용 요약 |

## 활성화

훅은 `.claude/settings.json`에서 `hooks` 키로 활성화합니다.
훅 명세는 `hooks/hooks.json`을 참조합니다.

## 비활성화

특정 훅을 비활성화하려면 `hooks.json`에서 해당 항목을 제거하거나
`.claude/settings.local.json`에서 오버라이드하세요.
