---
name: pill-arch-check
description: ArchUnit으로 DDD 레이어 의존 규칙을 검증한다.
---

# /pill-arch-check

## 사용법

```
/pill-arch-check                # 전체 컨텍스트 검증
/pill-arch-check {context}      # 특정 컨텍스트만
```

## 검증 규칙

1. **domain 격리**: domain은 presentation/application/infrastructure에 의존하지 않는다
2. **presentation 격리**: presentation은 infrastructure에 직접 의존하지 않는다
3. **application 격리**: application은 infrastructure 구현체에 의존하지 않는다 (Port 인터페이스만)
4. **Bounded Context 격리**: 컨텍스트 간 직접 의존 금지, application.port 통해서만

## 실행

```bash
./gradlew test --tests "*ArchitectureTest"
```

## 실패 시

위반 클래스 + 메서드 단위로 보고. 위반은 즉시 수정 (회피 금지).

## 참조

- `rules/java/ddd-layered.md`: 레이어 의존 규칙
