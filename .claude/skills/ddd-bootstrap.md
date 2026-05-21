---
name: ddd-bootstrap
description: 새 Bounded Context의 4-레이어 골격(presentation/application/domain/infrastructure)을 생성하는 워크플로우 스킬.
---

# DDD Bootstrap

## 목적

새 Bounded Context를 추가할 때 표준 4-레이어 골격을 누락 없이 생성한다.

## 사전 확인

- [ ] 컨텍스트 이름을 Ubiquitous Language에 등록했는가?
- [ ] 다른 컨텍스트와 책임 중복이 없는가?
- [ ] Aggregate Root와 ID 참조 관계를 정의했는가?

## 단계

### 1. 디렉터리 생성
```
src/main/java/com/pillmate/{context}/
├── presentation/
├── application/
│   └── port/                   # outbound port (Repository 인터페이스 등)
├── domain/
│   ├── model/                  # Entity, ValueObject
│   ├── repository/             # Repository 인터페이스
│   ├── service/                # 도메인 서비스
│   └── event/                  # 도메인 이벤트
└── infrastructure/
    ├── persistence/            # JPA Entity, Repository 구현
    ├── external/               # 외부 API 어댑터
    └── cache/                  # 캐시 어댑터

src/test/java/com/pillmate/{context}/
├── presentation/               # @WebMvcTest
├── application/                # UseCase 단위 테스트
├── domain/                     # 순수 단위 테스트
└── infrastructure/             # @DataJpaTest + Testcontainers
```

### 2. Aggregate Root 작성 (TDD)
- [ ] domain 테스트 먼저 작성 (RED)
- [ ] Aggregate Root 클래스 작성 (GREEN)
- [ ] ValueObject 분리 (REFACTOR)

### 3. Repository 인터페이스 정의
- [ ] domain/repository에 인터페이스만
- [ ] 메서드는 도메인 용어로 (`findActiveByGroup`, NOT `findByGroupIdAndStatus`)

### 4. UseCase 작성 (TDD)
- [ ] application 테스트 (Mock Repository) 먼저
- [ ] UseCase 작성
- [ ] `@Transactional` 경계는 UseCase에만

### 5. Infrastructure 구현
- [ ] JPA Entity (domain 모델과 분리 또는 매핑)
- [ ] Repository 구현
- [ ] `@DataJpaTest` + Testcontainers로 검증

### 6. Presentation
- [ ] REST DTO 정의 (`{Action}Request`, `{Resource}Response`)
- [ ] Controller 작성
- [ ] `@WebMvcTest`로 검증

### 7. 의존 방향 검증
ArchUnit으로 다음을 검증:
- domain은 외부에 의존하지 않는다
- presentation은 infrastructure에 의존하지 않는다
- application은 infrastructure 구현체에 의존하지 않는다

```java
@ArchTest
static final ArchRule domain_should_not_depend_on_other_layers =
    noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "..presentation..", "..application..", "..infrastructure.."
        );
```

## 체크리스트 (PR 머지 전)

- [ ] 4 레이어 모두 존재
- [ ] 각 레이어에 테스트 존재
- [ ] ArchUnit 의존 규칙 통과
- [ ] Ubiquitous Language 사용
- [ ] domain 클래스에 Spring 어노테이션 없음 (또는 격리 명시)
- [ ] 커버리지 80% 이상

## 참조

- `agents/ddd-modeler.md`: DDD 모델러 에이전트
- `agents/spring-boot-architect.md`: 모듈 구조
- `rules/java/ddd-layered.md`: 레이어 의존 규칙
