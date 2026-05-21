---
name: tdd-cycle
description: TDD Red → Green → Refactor 사이클을 강제 진행하는 워크플로우 스킬. 모든 도메인/유스케이스 코드 작성 시 사용한다.
---

# TDD Cycle

## 목적

PillMate의 모든 비즈니스 로직은 TDD 사이클로 작성한다.
이 스킬은 사이클을 절대 건너뛰지 않도록 강제한다.

## 워크플로우

### 단계 0: 사이클 시작 전 확인
- [ ] 작성할 동작이 한 가지인가? (Yes → 진행, No → 분할)
- [ ] 어느 Bounded Context의 어느 레이어인가? (domain/application/presentation/infrastructure)
- [ ] 테스트 파일 경로 결정 (`src/test/java/com/pillmate/{context}/{layer}/`)

### 단계 1: RED — 실패 테스트 작성
- [ ] 테스트 메서드명 = 한국어 문장 (`@DisplayName`)
- [ ] given-when-then 주석 구분
- [ ] AssertJ로 단언 (`assertThat`)
- [ ] **테스트 실행 → 실패 확인** (컴파일 에러도 RED의 일부)
- [ ] 실패 메시지가 명확한가? (단순 `null pointer` 금지)

```java
@Test
@DisplayName("같은 시간대 같은 약 중복 스케줄 등록 시 실패")
void schedule_duplicateAtSameTime_throws() {
    // given
    var schedule = ScheduleFixture.morning("metformin");
    scheduleRepository.save(schedule);

    // when / then
    assertThatThrownBy(() -> service.create(schedule.duplicate()))
        .isInstanceOf(DuplicateScheduleException.class)
        .hasMessageContaining("이미 등록된 스케줄");
}
```

### 단계 2: GREEN — 최소 코드로 통과
- [ ] 우아함보다 통과 우선
- [ ] 하드코딩 OK (다음 사이클에서 일반화)
- [ ] 기존 다른 테스트가 깨지지 않는지 확인 (`./gradlew test`)

### 단계 3: REFACTOR — 리팩토링
- [ ] 중복 제거 (Three strikes rule)
- [ ] 명명 개선 (Ubiquitous Language 준수)
- [ ] 책임 분리 (단일 책임 원칙)
- [ ] 매 리팩토링 후 전체 테스트 실행
- [ ] 테스트 코드도 리팩토링 대상

### 단계 4: 커밋
- [ ] `git status` 확인 (src + test 함께 커밋)
- [ ] 커밋 메시지: `[domain] 동작 설명 (TDD)` 형식
- [ ] 한 커밋 = 한 사이클

## 사이클 위반 시 행동

| 위반 | 조치 |
|------|------|
| 테스트 없이 src 작성 | 즉시 중단, RED 단계로 복귀 |
| GREEN을 너무 일찍 떠남 | 다시 GREEN 단계 복귀 |
| Refactor에서 테스트 실패 | git reset, 이전 GREEN 상태로 복귀 |

## 의료 도메인 추가 규칙

다음 도메인은 100% 커버리지 강제:
- 병용금기 검증 (`drug.domain.InteractionChecker`)
- 용량 계산 (`drug.domain.Dosage`)
- 복용 체크 상태 전이 (`doselog.domain.DoseLog`)
- 스케줄 시간 충돌 (`schedule.domain.ScheduleConflict`)

## 도구

```bash
# 단위 테스트만 빠르게
./gradlew test --tests "*DomainTest"

# 커버리지 리포트
./gradlew jacocoTestReport

# 실패한 테스트만 재실행
./gradlew test --rerun-tasks
```

## 참조

- `agents/tdd-coach.md`: TDD 코치 에이전트
- `rules/common/tdd-cycle.md`: TDD 코딩 규칙
- `rules/java/junit.md`: JUnit 5 사용 규칙
