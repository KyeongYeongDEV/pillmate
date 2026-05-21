---
name: ddd-layered
description: DDD 레이어드 아키텍처 의존 규칙 — ArchUnit으로 강제
---

# DDD Layered Architecture Rules

## 의존 방향

```
presentation  →  application  →  domain
                       ↓
                infrastructure  →  domain
```

| 레이어 | 의존 가능 | 의존 금지 |
|--------|-----------|-----------|
| presentation | application | domain, infrastructure |
| application | domain, application.port | infrastructure 구현체 |
| domain | (없음) | 모든 외부 |
| infrastructure | domain | presentation, application |

## ArchUnit 강제 규칙

```java
@AnalyzeClasses(packages = "com.pillmate")
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_outer_layers =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..presentation..", "..application..", "..infrastructure.."
            );

    @ArchTest
    static final ArchRule presentation_should_not_depend_on_infrastructure =
        noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule layered_architecture =
        layeredArchitecture().consideringAllDependencies()
            .layer("presentation").definedBy("..presentation..")
            .layer("application").definedBy("..application..")
            .layer("domain").definedBy("..domain..")
            .layer("infrastructure").definedBy("..infrastructure..")
            .whereLayer("presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("application").mayOnlyBeAccessedByLayers("presentation")
            .whereLayer("domain").mayOnlyBeAccessedByLayers(
                "presentation", "application", "infrastructure")
            .whereLayer("infrastructure").mayNotBeAccessedByAnyLayer();

    @ArchTest
    static final ArchRule contexts_should_not_depend_on_each_other =
        slices()
            .matching("com.pillmate.(*)..")
            .should().notDependOnEachOther()
            .ignoreDependency(
                resideInAPackage("..common.."),
                anyClass());
}
```

## Bounded Context 격리

- 컨텍스트 간 직접 import 금지
- 통신은 `application.port` 인터페이스로
- 공유는 `common/` (최소화, 변경 시 모두에게 영향)

## domain 레이어 추가 규칙

- Spring 어노테이션 최소 (Phase 1: `@Entity` 예외 허용, 나머지 금지)
- 외부 라이브러리 호출 금지
- 정적 의존성 금지 (`LocalDateTime.now()` → `Clock` 주입)
- 트랜잭션 모름

## 위반 시

- 빌드 실패 (CI에서 `./gradlew test` 단계)
- 즉시 수정 (회피 금지)
- 회피가 필요하면 PR에서 명시적 논의 후 예외 등록

## 참조

- `agents/ddd-modeler.md`
- `commands/pill-arch-check.md`
