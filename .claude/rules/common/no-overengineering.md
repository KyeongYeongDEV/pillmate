---
name: no-overengineering
description: 오버엔지니어링 방지 규칙 — Phase 1은 단순하게, 진화는 운영 데이터 기반으로
---

# No Overengineering

## 핵심 원칙

> "MVP는 빠르게, 시스템은 점진적으로"
>
> Phase 1에서 MSA, Kafka, Outbox를 도입하면 면접관이 "오버엔지니어링 아닌가?" 라고 묻는다.
> 구체적 운영 문제를 근거로 점진적으로 도입한다.

## Phase별 금지/허용

| 기술 | Phase 1 | Phase 2 | Phase 3 | Phase 4 |
|------|:-------:|:-------:|:-------:|:-------:|
| Monolith | ✅ | ✅ | ❌ | ❌ |
| Spring Boot 단일 | ✅ | ✅ | ❌ | ❌ |
| Resilience4j | ⚠️ 필수 부분만 | ✅ | ✅ | ✅ |
| Redis 캐싱 | ⚠️ 식약처만 | ✅ 전반 | ✅ | ✅ |
| MSA 분리 | ❌ 금지 | ❌ 금지 | ✅ | ✅ |
| gRPC | ❌ | ❌ | ✅ | ✅ |
| Kafka | ❌ 금지 | ❌ 금지 | ⚠️ 검토 | ✅ |
| Outbox Pattern | ❌ | ❌ | ⚠️ | ✅ |
| Saga | ❌ | ❌ | ❌ | ⚠️ |

## 도입 트리거 기준

새 기술 도입은 다음 중 하나가 충족되어야 한다:

1. **구체적 운영 문제 3회 이상 발생**
2. **현재 도구로 해결 불가 확인**
3. **도입 후 운영 비용 증가를 감당 가능**

## 안티 패턴

- 면접용으로 기술 도입 ❌ (반드시 운영 문제 근거)
- "나중에 필요할까봐" 추상화 레이어 추가 ❌
- 사용하지 않는 인터페이스/팩토리 ❌

## 참조

- `agents/msa-evolution-strategist.md`
- `contexts/evolution-story.md`
