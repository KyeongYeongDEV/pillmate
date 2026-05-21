---
name: tdd-cycle
description: PillMate TDD 사이클 코딩 규칙
---

# TDD Cycle Rules

## 절대 규칙

1. **테스트 없는 도메인 코드 금지**
   - `src/main/.../domain/` 클래스는 반드시 대응 테스트 존재
   - 위반 시 PR 차단

2. **사이클 분리**
   - 한 커밋 = 한 사이클 (RED + GREEN, 또는 REFACTOR)
   - 큰 변경은 여러 커밋으로 분할

3. **RED 검증**
   - GREEN 코드를 작성하기 전에 테스트가 실패해야 한다
   - 컴파일 에러도 RED의 일부

## 명명 규칙

```java
// 메서드명: 영문 snake_case 또는 camelCase
@Test
@DisplayName("같은 시간대 같은 약 중복 스케줄 등록 시 실패")
void schedule_duplicateAtSameTime_throws() { ... }
```

- `@DisplayName`은 한국어 도메인 문장
- 테스트 메서드명: `{대상}_{조건}_{기대결과}`

## 구조 규칙

```java
@Test
void test_name() {
    // given
    var input = ...;

    // when
    var result = subject.action(input);

    // then
    assertThat(result).isEqualTo(expected);
}
```

- given-when-then 주석 필수
- AssertJ 사용 (`assertThat`)
- JUnit 단순 `assertEquals` 금지

## 단언 규칙

- 한 테스트 = 한 동작 (assertion 여러 개 OK, 다중 동작 금지)
- 예외 검증: `assertThatThrownBy` + 메시지/타입 검증
- 시간/랜덤: `Clock`, `Random`을 주입해 결정적으로

## 커버리지

| 레이어 | 최소 커버리지 |
|--------|--------------|
| domain | 90% |
| application | 85% |
| presentation | 70% (Slice Test) |
| infrastructure | 60% (Testcontainers) |

## 의료 도메인 100% 강제

- `drug.domain.InteractionChecker`
- `drug.domain.Dosage`
- `doselog.domain.DoseLog` 상태 전이
- `schedule.domain.ScheduleConflict`

## 참조

- `skills/tdd-cycle.md`
- `agents/tdd-coach.md`
