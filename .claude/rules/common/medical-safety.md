---
name: medical-safety
description: 의료 도메인 안전 규칙 — 환자 안전 최우선
---

# Medical Safety Rules

> "약은 잘못 먹으면 안 됩니다."
>
> 환자 안전이 걸린 도메인에서 다음 규칙은 절대 위반 불가다.

## 절대 금지

| 금지 사항 | 이유 |
|----------|------|
| 출처 없는 의료 정보 응답 | 환자가 잘못된 정보로 약 복용 가능 |
| 식약처 DB에 없는 약품 자동 등록 | 잘못된 약 등록 위험 |
| 병용금기 검증 누락 | 약물 상호작용 사고 |
| 신뢰도 낮은 OCR 결과 자동 등록 | 잘못된 약 등록 위험 |
| 환자 동의 없는 보호자 데이터 접근 | 의료 데이터 침해 |
| LLM 응답을 그대로 의료 조언으로 표시 | 환각 위험 |

## 필수 강제

1. **출처 명시**
   - 모든 의료 정보는 "출처: 식약처" 또는 "출처: 대한○○학회" 포함
   - 출처 없으면 응답 차단

2. **병용금기 사전 검증**
   - 처방전 등록 시 약 쌍별 검증
   - 챗봇 응답 전 검증

3. **신뢰도 임계치**
   - OCR 신뢰도 < 0.7 → 사용자 확인 단계
   - RAG Faithfulness < 0.95 → "확인 불가" fallback

4. **감사 로그**
   - 모든 의료 데이터 접근 기록 (3년 보관)
   - 환자별 접근 이력 조회 가능

5. **개인정보 보호**
   - 처방전 이미지 SSE-S3 암호화 필수
   - 객체 키에 환자 식별자 포함 금지
   - 로그에 처방 내용 직접 출력 금지

## 응답 fallback 표준

```
신뢰도 부족 시:
"정확한 정보를 확인할 수 없습니다.
 약사 또는 의사와 상담해 주세요."

병용금기 발견 시:
"⚠️ 이 약은 [복용 중인 약]과 함께 복용하면 위험할 수 있습니다.
 출처: 식품의약품안전처 병용금기 목록"
```

## 코드 검증 패턴

```java
public DrugInfoResponse describe(DrugCode code) {
    var drug = drugRepository.findByCode(code)
        .orElseThrow(() -> new DrugNotFoundException(code));

    var info = llm.generate(drug);

    if (!info.hasSource()) {
        return DrugInfoResponse.unknownSource();   // fallback
    }
    if (info.faithfulness() < 0.95) {
        return DrugInfoResponse.requireSpecialist(); // fallback
    }
    return info.withSource(MFDS_SOURCE);
}
```

## 참조

- `agents/medical-domain-validator.md`
- `contexts/medical-domain.md`
