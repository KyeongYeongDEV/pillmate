---
name: evolution-story
description: 점진적 시스템 진화 스토리 — 포트폴리오/면접용 narrative
---

# Evolution Story

PillMate의 핵심 차별화 메시지를 면접에서 일관되게 전달하기 위한 narrative입니다.

## Core Message

> "포트폴리오에서 MSA를 처음부터 도입하면 오버엔지니어링입니다.
> MVP를 단일 서버로 빠르게 만들고, 운영 중 발견한 구체적 문제를 해결하며
> 자연스럽게 MSA로 진화시켰습니다. 실무의 점진적 시스템 개선 방식입니다."

## Phase별 스토리

### Phase 1 — 빠르게 검증
- Spring Boot 단일 + FastAPI 분리 (언어 분리, 단일 인스턴스)
- PostgreSQL + Redis + S3
- 목표: **3~4주 안에 동작하는 MVP**
- 메시지: "검증 가능한 가설을 먼저"

### Phase 2 — 운영하며 발견한 문제 해결
운영 중 다음 문제가 발생:
1. AI 서버 장애로 약 등록까지 죽음
   → Circuit Breaker (Resilience4j) + 수동 입력 fallback
2. 처방전 OCR 동기 처리로 응답 지연
   → 비동기 큐 (Redis Stream) + 진행 상태 폴링
3. 식약처 API 한도 초과
   → Redis 캐싱 + Token Bucket Rate Limit
4. 같은 처방전 재업로드로 LLM 호출 중복
   → 이미지 해시 캐싱 (30% 절감)
5. 같은 약 정보 질문 반복
   → RAG FAQ 캐싱 (50% 절감)
6. RAG 정확도 부족
   → Hybrid Retrieval (BM25 + Dense), KMMLU 패턴 재사용

메시지: "운영 데이터에 기반한 구체적 개선"

### Phase 3 — 트래픽 증가로 분리 필요
운영 중 다음 신호 발생:
- AI 서버 장애가 약 등록 기능까지 죽이는 일이 반복 (>= 3회/월)
- 단일 DB 부하 75% 지속
- 백엔드/AI 팀이 같은 코드베이스 수정 → 배포 충돌

→ AI Service, Drug Service 분리 (gRPC + Protobuf)
→ DB per Service
→ OpenTelemetry 분산 트레이싱

메시지: "분리는 트리거 후에"

### Phase 4 — 출시 결정 후 알림 완성도
- 의료 알림 누락 = 환자 안전 위협
- WebFlux + SSE 그룹 fan-out
- Kafka + Outbox Pattern (트랜잭션-알림 일관성)
- Idempotency Key + DLQ

메시지: "도달성 보장"

## 면접 Q&A 준비

**Q: Phase 1에 Kafka 안 쓴 이유?**
A: "처음부터 Kafka를 도입하면 오버엔지니어링입니다. Phase 1에 트랜잭션-알림
일관성 문제가 발생하지 않았고, Phase 4 알림 시점에 도입했습니다."

**Q: MSA 분리 기준?**
A: "구체적 운영 신호 3개를 기준으로 했습니다. AI 장애 전파, DB 부하, 배포 충돌.
각각이 모놀리스에서 해결 불가하다는 게 확인되어 분리했습니다."

**Q: RAG 성능 어떻게 개선?**
A: "KMMLU 프로젝트에서 검증한 Hybrid Retrieval + Dynamic Top-K 패턴을
의료 도메인에 재적용했습니다. Faithfulness 0.95 이상을 목표로 RAGAS 평가를
CI에 통합했습니다."

**Q: LLM 비용 어떻게 절감?**
A: "하이브리드 모델 라우팅 + 이미지 해시 캐싱 + RAG FAQ 캐싱 3단으로
GPT-4o 단독 대비 92% 절감했습니다. 월 $11로 운영."

## 참조

- `agents/msa-evolution-strategist.md`
- `rules/common/no-overengineering.md`
