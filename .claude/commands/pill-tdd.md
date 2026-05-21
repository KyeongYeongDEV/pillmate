---
name: pill-tdd
description: TDD Red → Green → Refactor 사이클을 시작한다. 도메인 동작 하나에 대한 사이클을 강제 진행.
target: skills/tdd-cycle.md
---

# /pill-tdd

## 사용법

```
/pill-tdd {bounded-context} {feature-summary}
```

예시:
```
/pill-tdd prescription "처방전 등록 시 약품이 식약처 DB에 없으면 실패"
/pill-tdd schedule "같은 시간대 같은 약 중복 스케줄 거부"
/pill-tdd doselog "PENDING -> TAKEN 상태 전이"
```

## 실행 흐름

1. 입력 검증 (context 이름, feature 명확성)
2. `skills/tdd-cycle.md` 호출
3. RED 단계 안내 (테스트 파일 경로 + 템플릿 제공)
4. GREEN 단계 안내 (최소 구현 가이드)
5. REFACTOR 단계 안내 (체크리스트)
6. 커밋 메시지 제안

## 사전 조건

- Bounded Context가 존재해야 함 (없으면 `/pill-ddd-new` 먼저)
- 테스트 환경 구성됨 (JUnit 5, AssertJ, Testcontainers)

## 출력

- RED 테스트 파일 경로 + 템플릿
- 실행 명령: `./gradlew test --tests "*{TestClass}*"`
- 진행 상태 추적 (Task System)
