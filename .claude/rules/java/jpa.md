---
name: jpa
description: Spring Data JPA 사용 규칙
---

# JPA Rules

## Aggregate

- Aggregate Root만 Repository를 가진다
- 자식 Entity는 Root를 통해서만 접근
- Aggregate 간 참조는 ID로만 (`Long prescriptionId`, NOT `Prescription`)

## Entity

```java
@Entity
@Table(name = "prescriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long careGroupId;          // 다른 Aggregate는 ID로

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrescribedDrug> drugs = new ArrayList<>();

    ...
}
```

- `@NoArgsConstructor(access = PROTECTED)` 강제
- public setter 금지 (Builder 또는 도메인 메서드)
- 양방향 연관 최소화 (방향 명확)

## 페치 전략

- 기본 `FetchType.LAZY`
- N+1 시 `@EntityGraph` 또는 `JOIN FETCH`
- `EAGER` 사용 금지 (예외: 작은 enum-like)

## 쿼리

- 우선순위: 메서드 이름 → `@Query` JPQL → QueryDSL
- 동적 쿼리는 QueryDSL
- 네이티브 쿼리는 infrastructure 레이어에만

## 트랜잭션

- 트랜잭션 안에서 외부 호출 금지 (LLM, S3, 식약처 API)
- 외부 호출 후 트랜잭션은 application 레이어에서 분리

## 성능

- Batch Insert: `hibernate.jdbc.batch_size = 100`
- DoseLog는 partitioning + batch (월 900만 건 대비)
- 조회 페이지네이션 강제 (`Pageable`)

## 마이그레이션

- Flyway 사용 (`src/main/resources/db/migration/`)
- 파일명: `V{버전}__{설명}.sql`
- 절대 기존 마이그레이션 수정 금지 (새 버전 추가)

## 금지

- `@Data` (모든 getter/setter 자동 생성 → 의도치 않은 변경)
- public setter
- Entity 내 비즈니스 로직 외 코드 (외부 호출, 캐시 접근 등)
