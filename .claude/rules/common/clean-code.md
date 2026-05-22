# Clean Code Rules

> 사용자 지정 클린코드 규칙. 모든 신규/수정 코드에 강제 적용.
> 위반 시 PR 차단 또는 리팩토링 요구.

## 1. 주석 생략

- 코드는 **자기 설명적(self-documenting)** 이어야 한다. 주석으로 설명하지 말고 좋은 이름·구조로 말하라.
- **예외 1**: 비자명한 비즈니스 의도 (Why) — 왜 이렇게 했는지 코드만으로 알기 어려운 경우 한 줄.
- **예외 2**: 외부 제약(법규, 식약처 정책, 의료 안전 fallback 사유) — 한 줄로 출처 인용.
- 금지: WHAT 주석 (`// increment counter`), 메서드 위 javadoc-style WHAT 주석.
- 금지: 변경 이력·작성자·날짜 주석 (git이 담당).

```java
// 나쁨
int i = 0;  // 카운터 초기화

// 좋음 — 주석 없음, 이름으로 설명
int processedCount = 0;
```

## 2. SRP (Single Responsibility Principle)

- 한 클래스 = 하나의 책임. 한 메서드 = 하나의 행위.
- 다른 추상화 수준의 코드가 한 메서드 안에 섞이면 **추출 대상**.
- 반복문 본문이 3줄 이상이면 메서드 추출 검토.
- 조건문 분기마다 다른 행위면 분기별 메서드 추출.

## 3. 메서드 길이 제한

| 레이어 | 메서드 길이 (이상적) | 절대 한도 |
|--------|---------------------|-----------|
| domain | ≤25줄 | 40줄 |
| **application/service** | **≤20줄** | **30줄** |
| presentation (Controller) | ≤15줄 | 25줄 |
| infrastructure | ≤30줄 | 50줄 |

- 줄 수에는 시그니처·`{` `}`·빈 줄 포함.
- 한도 초과 시 private 메서드로 추출. 추출 메서드는 한 가지만 한다.

## 4. 반복문·조건문·조회 → private 추출

다음 패턴은 즉시 private 메서드로 분리:

```java
// 나쁨
public void registerPrescription(PrescriptionRequest req) {
    if (req.getDrugs() == null || req.getDrugs().isEmpty()) {
        throw new InvalidPrescriptionException("약 목록이 비어있습니다.");
    }
    for (DrugItem d : req.getDrugs()) {
        if (d.getQuantity() <= 0) {
            throw new InvalidQuantityException(d.getCode());
        }
    }
    Drug drug = drugRepository.findByCode(req.getDrugs().get(0).getCode())
        .orElseThrow(() -> new DrugNotFoundException(req.getDrugs().get(0).getCode()));
    // ...
}

// 좋음
public void registerPrescription(PrescriptionRequest req) {
    validateDrugs(req.getDrugs());
    Drug primaryDrug = findPrimaryDrug(req.getDrugs());
    // ...
}

private void validateDrugs(List<DrugItem> drugs) {
    requireNonEmpty(drugs);
    drugs.forEach(this::requirePositiveQuantity);
}

private Drug findPrimaryDrug(List<DrugItem> drugs) {
    return drugRepository.findByCode(drugs.get(0).getCode())
        .orElseThrow(() -> new DrugNotFoundException(drugs.get(0).getCode()));
}
```

### 추출 트리거 체크리스트

- [ ] 반복문 본문 ≥ 3줄 → 메서드 추출
- [ ] 조건문 분기 본문 ≥ 3줄 → 메서드 추출
- [ ] 같은 형태의 조회·검증이 2회 이상 → private 메서드로 통합
- [ ] 메서드 안에 "이걸 하기 위해 저걸 한다" 가 섞임 → 분리

## 5. 명명

- Ubiquitous Language (`.claude/contexts/ubiquitous-language.md`) 용어만 사용.
- 약어 금지 (`req` → `request`, `cnt` → `count`).
- 단, 도메인에서 표준화된 약어는 허용 (`OTC`, `MFDS`, `KD code`).
- private 메서드명은 동사로 시작 (`validateXxx`, `findXxx`, `buildXxx`, `appendXxx`).
- boolean 반환은 `is/has/can` 접두.

## 6. 매직 넘버·문자열 → 상수

```java
// 나쁨
if (drug.getConfidence() < 0.7) { ... }

// 좋음
private static final double OCR_MIN_CONFIDENCE = 0.7;
if (drug.getConfidence() < OCR_MIN_CONFIDENCE) { ... }
```

- 비즈니스 의미 있는 상수는 도메인 객체 또는 enum에 배치.
- 의료 도메인 임계치(`OCR_MIN_CONFIDENCE`, `RAG_MIN_FAITHFULNESS=0.95`)는 `.claude/rules/common/medical-safety.md` 와 동기화.

## 7. 빈 줄·정렬

- 한 메서드 안에서 논리 블록 사이 빈 줄 1줄 허용 (단, 메서드가 짧으면 안 씀).
- import 정렬 + 그룹 (java → 외부 → 내부 com.pillmate).
- 메서드 정렬: public → package-private → protected → private.

## 8. 예외 처리

- `catch (Exception e) {}` 금지 (swallow).
- 도메인 예외는 unchecked + 명확한 메시지.
- try-catch 본문이 길어지면 한 메서드로 추출.

## 9. 가독성 우선 트레이드오프

- 성능 vs 가독성 → 측정 후 결정. **기본은 가독성**.
- 인라인 vs 추출 → 한 번 쓰는 라인이라도 의미가 비자명하면 메서드 추출.
- 추상화 vs 직접 코드 → "두 번 쓰면 검토, 세 번 쓰면 추출" (Three Strikes Rule).

## 10. 자기 검증 체크리스트 (커밋 전)

- [ ] 메서드 시그니처만 보고 그 메서드가 무엇을 하는지 알 수 있는가
- [ ] 코드를 위→아래로 읽었을 때 신문 기사처럼 추상화 수준이 일정한가
- [ ] 각 메서드의 들여쓰기 깊이가 2단계 이내인가 (Boolean flag·조건 nesting 줄이기)
- [ ] 같은 코드를 두 번 작성한 곳이 없는가
- [ ] 매직 넘버·문자열이 없는가
- [ ] WHAT 주석이 없는가

## 참조

- `.claude/rules/java/spring-boot.md` — Spring 특화 규칙 (보강)
- `.claude/rules/java/jpa.md` — Aggregate·Entity 규칙
- `.claude/rules/common/tdd-cycle.md` — TDD 사이클 (Refactor 단계에서 본 규칙 적용)
- `.claude/contexts/ubiquitous-language.md` — 도메인 용어
