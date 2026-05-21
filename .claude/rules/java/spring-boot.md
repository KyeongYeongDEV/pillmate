---
name: spring-boot
description: Spring Boot 3 + Java 17 코딩 규칙
---

# Spring Boot Rules

## 버전

- Java 17 (LTS)
- Spring Boot 3.3.x
- Gradle (Kotlin DSL)

## 패키지 구조

`src/main/java/com/pillmate/{context}/{layer}/`

각 레이어:
- `presentation` — `Controller`, REST DTO (`Request`/`Response`)
- `application` — `UseCase` (인터페이스), `*Service` (구현), `Port`, App DTO
- `domain` — Entity, ValueObject, DomainService, Repository (인터페이스)
- `infrastructure` — JPA, 외부 API, 캐시 구현

## DI 규칙

- 생성자 주입만 사용 (`@Autowired` 필드 주입 금지)
- Lombok `@RequiredArgsConstructor` 권장
- final 필드 강제

```java
@Service
@RequiredArgsConstructor
public class RegisterPrescriptionService implements RegisterPrescriptionUseCase {
    private final PrescriptionRepository prescriptionRepository;
    private final DrugRepository drugRepository;
    private final OcrPort ocrPort;
    ...
}
```

## 트랜잭션

- `@Transactional`은 application 레이어에만
- readonly 명시: `@Transactional(readOnly = true)`
- 트랜잭션 안에서 외부 API 호출 금지

## 예외 처리

- 도메인 예외는 unchecked (`RuntimeException` 상속)
- 글로벌 예외 핸들러는 `common/exception/GlobalExceptionHandler`
- 응답 본문은 통일된 `ErrorResponse` 스키마

## 보안

- JWT: Spring Security 사용
- 그룹 권한: `@PreAuthorize("@careGroupGuard.canAccess(#groupId)")`
- 비밀번호: BCrypt
- 환경변수: `application-{profile}.yml` + AWS Secrets Manager

## 로깅

- SLF4J (`@Slf4j`)
- 환자 식별 정보 로깅 금지 (해시/마스킹)
- 구조화 로깅 (JSON): logback-spring.xml

## 금지

- `@Autowired` 필드 주입
- `@Component`를 controller/service에 직접 (구체 어노테이션 사용)
- `System.out.println` (SLF4J 사용)
- 예외 swallow (`catch (Exception e) {}` 금지)
- 환자 정보 로그 출력
