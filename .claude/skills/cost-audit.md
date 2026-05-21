---
name: cost-audit
description: LLM/AWS 비용을 주기적으로 감사하고 절감 액션을 도출하는 워크플로우.
---

# Cost Audit

## 주기

- **일간**: LLM 비용 자동 집계 (Slack 리포트)
- **주간**: AWS 비용 + 캐시 hit rate 검토
- **월간**: 전체 감사 + 다음 달 예산 조정

## 일간 체크

```
□ Gemini 토큰 사용량 (모델별)
□ FastAPI 요청 수
□ Redis 캐시 hit rate
□ 비정상 사용자 (LLM 호출 한도 초과)
```

## 비용 임계치

| 항목 | Warning | Critical |
|------|---------|----------|
| LLM 일일 비용 | $5 | $15 |
| S3 전송량 | 10GB/일 | 50GB/일 |
| RDS CPU | 70% | 90% |
| Redis 메모리 | 70% | 90% |

## 절감 액션 (Phase별)

| 문제 | Phase 1 | Phase 2 | Phase 3+ |
|------|---------|---------|----------|
| LLM 비용 ↑ | 모델 다운그레이드, 캐싱 | 프롬프트 압축 | Self-hosted (Llama) |
| S3 비용 ↑ | 라이프사이클 (IA, Glacier) | VPC Endpoint | CDN 정적화 |
| DB 부하 | 인덱스 추가 | 읽기 복제본 | DB per Service |

## 면접 어필 (포트폴리오 가치)

> "MVP 단계에서 LLM 비용 92% 절감을 달성했습니다.
> 하이브리드 모델 라우팅 + 이미지 해시 캐싱 + RAG FAQ 캐싱 3단으로
> GPT-4o 단독 대비 $146 → $11 (월) 절감했고,
> 비용 알람으로 비정상 사용 자동 차단 메커니즘도 구축했습니다."

## 참조

- `agents/cost-optimizer.md`: 비용 최적화 에이전트
- `rules/common/cost-aware.md`: 비용 의식 코딩 규칙
