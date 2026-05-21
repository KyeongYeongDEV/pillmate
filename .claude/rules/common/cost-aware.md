---
name: cost-aware
description: 비용 의식 코딩 규칙 — LLM/AWS 비용을 항상 고려
---

# Cost Aware Coding

## LLM 호출 규칙

1. **캐시 우선**
   - 모든 LLM 호출 전 Redis 캐시 확인
   - 이미지 OCR: SHA-256 해시 키
   - 챗봇: 질문 임베딩 유사도 > 0.92 → 캐시 hit

2. **모델 라우팅**
   - 기본: gemini-2.5-flash-lite (테스트, 단순 요약)
   - 정확도 필요: gemini-2.5-flash (OCR, 챗봇, 추천)
   - GPT-4o, Claude 3.5 사용 금지 (비용 X10+)

3. **토큰 한도**
   - 사용자당 일 LLM 호출 10회
   - 초과 시 429 + "내일 다시" UX

4. **프롬프트 최적화**
   - 시스템 프롬프트는 캐시 활용 (Anthropic prompt caching)
   - 불필요한 컨텍스트 제거

## AWS 비용 규칙

1. **S3**
   - 30일 후 IA, 90일 후 Glacier IR 라이프사이클 강제
   - 객체 키 UUID (인덱싱 비용 절감)

2. **RDS**
   - Phase 1: t3.medium (싱글 AZ)
   - Phase 2+: 부하 데이터 기반 스케일

3. **Redis**
   - TTL 명시 강제 (영구 키 금지)
   - 메모리 정책: `allkeys-lru`

## 비용 가드 코드 패턴

```java
@RateLimiter(name = "user-llm-daily", fallbackMethod = "rateLimitFallback")
public ChatResponse chat(UserId userId, String question) { ... }

private ChatResponse rateLimitFallback(UserId userId, String question, Throwable e) {
    return ChatResponse.dailyLimitExceeded();
}
```

## 참조

- `agents/cost-optimizer.md`
- `skills/cost-audit.md`
