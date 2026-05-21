---
name: junit
description: JUnit 5 + AssertJ + Mockito 사용 규칙
---

# JUnit Rules

## 테스트 도구

- JUnit 5 (Jupiter)
- AssertJ (`assertThat`)
- Mockito (Mock)
- Testcontainers (PostgreSQL, Redis)
- ArchUnit (레이어 의존 검증)

## 패키지 구조

```
src/test/java/com/pillmate/{context}/
├── domain/          # 순수 단위 (Spring X)
├── application/     # @MockBean 또는 Mockito
├── presentation/    # @WebMvcTest
└── infrastructure/  # @DataJpaTest + Testcontainers
```

## 명명 규칙

```java
class PrescriptionTest {            // 도메인 단위
class RegisterPrescriptionServiceTest {  // UseCase
class PrescriptionControllerTest {  // @WebMvcTest
class PrescriptionRepositoryTest {  // @DataJpaTest
```

테스트 메서드: `{대상}_{조건}_{기대결과}`
```java
void register_whenDrugNotFound_throws() { ... }
void check_whenAlreadyTaken_isIdempotent() { ... }
```

`@DisplayName`은 한국어 문장.

## 단언

- `assertThat(actual).isEqualTo(expected)` (AssertJ)
- 컬렉션: `containsExactly`, `containsExactlyInAnyOrder`
- 예외: `assertThatThrownBy(() -> ...).isInstanceOf(...).hasMessageContaining(...)`
- JUnit 단순 assertion 금지 (`assertEquals`, `assertTrue`)

## Fixture

```java
public class ScheduleFixture {
    public static Schedule morning(String drugCode) {
        return Schedule.builder()
            .drugCode(new DrugCode(drugCode))
            .timeOfDay(TimeOfDay.MORNING)
            .build();
    }
}
```

- 픽스처 클래스로 데이터 생성 통일
- 픽스처는 `src/test/java/.../fixture/`

## Mock 규칙

- domain 테스트에 Mock 금지 (순수 단위)
- application 테스트에 Mock 허용 (Repository, Port)
- 과도한 Mock은 설계 문제 신호 (분리 재검토)

## 통합 테스트

```java
@SpringBootTest
@Testcontainers
class PrescriptionIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test");
    ...
}
```

- 통합 테스트는 별도 태그 (`@Tag("integration")`)
- 기본 빌드는 단위 테스트만, CI에서 통합 포함

## 금지

- `@SpringBootTest`를 도메인/UseCase 테스트에 사용 (느림)
- `Thread.sleep` (Awaitility 사용)
- 테스트 간 상태 공유 (각 테스트 격리)
- random 값 (결정적 fixture 사용)
