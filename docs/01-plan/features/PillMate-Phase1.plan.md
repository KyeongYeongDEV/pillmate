---
template: plan
version: 1.2
feature: PillMate-Phase1
date: 2026-05-21
author: 최경영
project: PillMate
version: 0.1.0-MVP
---

# PillMate Phase 1 (MVP) Planning Document

> **Summary**: 보호자 ↔ 노인/환자 그룹 기반 스마트 복약 관리 플랫폼의 MVP. 단일 서버 구조로 7가지 핵심 기능을 빠르게 검증.
>
> **Project**: PillMate
> **Version**: 0.1.0-MVP
> **Author**: 최경영
> **Date**: 2026-05-21
> **Status**: Draft
> **Reference**: [Notion 설계 문서](https://www.notion.so/PillMate-36681d8a13f08162a9f5f376ef021f74)

---

## 1. Overview

### 1.1 Purpose

**의료 도메인의 정확성**과 **그룹 기반 케어**를 핵심으로, 노인/만성질환자가 약을 빠뜨리지 않도록 보호자와 함께 관리하고 AI 기반 기능으로 안전한 약 복용을 보장하는 플랫폼의 **MVP(최소 기능 제품)**를 출시한다.

핵심 가치 명제:
1. **처방전 자동 등록** — 사진 한 장 → AI가 약 인식 → 자동 등록 (복약시간: 아점저 평균시간 기본값, 수정 가능)
2. **신뢰할 수 있는 정보** — 식약처 공공 데이터 + RAG 기반 검증
3. **그룹 중심 케어** — 보호자와 환자가 한 화면에서 함께 관리
4. **안전한 복약 상담** — 병용금기/부작용 정확 안내

### 1.2 Background

- **시장 검증**: Medisafe(글로벌 1000만+)가 Medfriend 그룹 알림으로 **복약 순응도 71% 개선** 임상 입증
- **국내 차별화**: 한국 의료 데이터(식약처 API)와 한국적 케어 문화(N:N 가족 모델) 결합
- **포트폴리오 전략**: "MVP는 빠르게, 시스템은 점진적으로" — 단일 서버 출시 후 발견된 문제를 기반으로 MSA로 진화 (오버엔지니어링 회피)

### 1.3 Related Documents

- Notion 설계 원본: https://www.notion.so/PillMate-36681d8a13f08162a9f5f376ef021f74
- 식약처 의약품 API: 공공데이터포털
- 연계 프로젝트: KMMLU (Hybrid Retrieval + Dynamic Top-K 패턴 재사용)
- Phase 2~4 로드맵: Notion 문서 § Phase 로드맵 참조

---

## 2. Scope

### 2.1 In Scope (Phase 1 MVP)

- [ ] **사용자/그룹 모델** — 더미 데이터 + 임시 userId (실제 인증은 우선순위 8번)
- [ ] **케어 그룹** — 생성/초대(QR + 초대코드)/참여/역할(보호자/환자)
- [ ] **처방전 사진 업로드** — S3 Pre-signed URL
- [ ] **처방전 OCR + 약 등록** — Gemini Vision + 식약처 매칭 + pgvector 유사도 검색
- [ ] **약 마스터 데이터** — 식약처 API 연동, 약 검색/상세 조회
- [ ] **복약 스케줄 관리** — 아점저 평균시간 기본값, 사용자 수정 가능
- [ ] **복용 체크 & 히스토리** — 완료/스킵/지연, 월별/주별 조회
- [ ] **AI 복약 상담 챗봇 (RAG)** — pgvector + Gemini-2.5-flash (테스트는 flash-lite)
- [ ] **AI 건강 관리 추천** — 처방 약 → 질환 추론 → 식단/운동 추천
- [ ] **회원가입/로그인** — 카카오, 구글 SSO + JWT (우선순위 마지막, MVP 후반)
- [ ] **Docker Compose 통합 배포** — Spring Boot + FastAPI + PostgreSQL + Redis
- [ ] **AWS EC2 단일 인스턴스 배포**

### 2.2 Out of Scope (Phase 2 이후로 미룸)

- 알림 시스템 (FCM Push, SSE) → Phase 4
- WebFlux + Kafka + Outbox Pattern → Phase 4
- MSA 분리 (User/Drug/AI 서비스) → Phase 3
- Circuit Breaker (Resilience4j) → Phase 2
- 비동기 OCR 큐 (Redis Stream) → Phase 2
- Prometheus + Grafana 모니터링 → Phase 2
- 부하 테스트 (Locust) → Phase 2
- 복약 패턴 자동 리포트(월 1회 자동 생성) → Phase 2
- LLM 응답 캐싱 (FAQ 자동 학습) → Phase 2

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | 더미 유저 + 임시 userId로 API 호출 가능 (인증 우회) | High | Pending |
| FR-02 | 케어 그룹 생성 + 초대 코드/QR로 참여 (보호자/환자 역할 지정) | High | Pending |
| FR-03 | 처방전 사진을 S3 Pre-signed URL로 직접 업로드 | High | Pending |
| FR-04 | Gemini Vision으로 처방전에서 약 이름 자동 추출 + pgvector 유사도 매칭으로 약품 정확 식별 | High | Pending |
| FR-05 | 식약처 API에서 약 상세 정보(효능/부작용/용법/약가) 조회 + Redis 캐싱 | High | Pending |
| FR-06 | 처방전 기반 복약 스케줄 자동 생성 (아침/점심/저녁/취침 전 기본값, 수정 가능) | High | Pending |
| FR-07 | 복용 체크 (완료/스킵/지연 + 타임스탬프) 및 월별/주별 히스토리 조회 | High | Pending |
| FR-08 | RAG 기반 챗봇으로 약 정보 Q&A 및 병용금기 확인 (출처 명시) | High | Pending |
| FR-09 | 처방 약 리스트 기반 질환 추론 및 식단/운동 추천 (RAG + LLM) | Medium | Pending |
| FR-10 | 카카오/구글 SSO + JWT 인증 (MVP 후반에 더미 → 실제 인증으로 교체) | Medium | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|--------------------|
| Performance | 처방전 OCR 응답 < 10s (동기 처리, 비동기는 Phase 2) | API 응답 시간 측정 |
| Performance | RAG 챗봇 응답 < 5s | API 응답 시간 측정 |
| Performance | 일반 CRUD API 응답 < 200ms (p95) | Spring Actuator 메트릭 |
| AI 정확도 | 처방전 OCR 약품명 매칭 정확도 ≥ 90% | 테스트 처방전 100건 수동 검증 |
| AI 정확도 | RAG 챗봇 답변에 출처 명시율 100% | LLM 응답 파싱 검증 |
| 비용 | LLM 월 비용 ≤ $11 (하이브리드 라우팅) | Gemini Console 모니터링 |
| 비용 | AWS 인프라 월 비용 ≤ $80 (EC2 t3.medium + RDS PG + Redis + S3) | AWS Cost Explorer |
| Security | 처방전 이미지 SSE-S3 서버측 암호화 (개인정보보호법) | S3 버킷 정책 검증 |
| Security | Pre-signed URL TTL 1시간 이내 | 버킷 정책 |
| Security | 사용자별 S3 접근 권한 분리 (FastAPI만 raw 읽기) | IAM Role 검증 |
| 데이터 보존 | 처방전 이미지 라이프사이클 (Standard → IA → Glacier IR) | S3 라이프사이클 정책 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] 7가지 핵심 기능 모두 동작 (Notion 문서 § ✨ 핵심 기능)
- [ ] Docker Compose로 로컬 환경 전체 실행 가능
- [ ] EC2에 단일 인스턴스 배포 완료, 외부에서 접근 가능
- [ ] 처방전 사진 → 자동 약 등록 → 스케줄 생성 → 복용 체크 → 챗봇 상담의 End-to-End 시나리오 통과
- [ ] 테스트 처방전 10건 이상으로 OCR 정확도 검증
- [ ] API 명세서 (OpenAPI/Swagger) 작성
- [ ] ERD 문서 작성 (User, CareGroup, Prescription, Drug, Schedule, DoseLog)

### 4.2 Quality Criteria

- [ ] Spring Boot 단위/통합 테스트 작성 (핵심 도메인 위주)
- [ ] FastAPI 핵심 RAG/OCR 로직 테스트
- [ ] 코드 리뷰 완료
- [ ] Lint/포맷 통과 (Checkstyle/Black)
- [ ] 빌드 성공 (Gradle, Poetry/pip)
- [ ] README + 실행 가이드 작성

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Gemini Vision OCR 한글 처방전 인식 정확도 부족 | High | Medium | Hybrid Retrieval (BM25 + Dense)로 약품명 매칭 보강, 사용자 수정 UI 제공 |
| 식약처 API 호출 한도 초과 | Medium | Medium | Redis 캐싱 (TTL 24h) + Token Bucket Rate Limit (Phase 2에서 본격 도입, MVP는 기본 캐싱) |
| pgvector 유사도 검색 정확도 부족 | High | Low | KMMLU 프로젝트에서 검증한 Dynamic Top-K 패턴 적용 |
| LLM 비용 폭증 | Medium | Low | 하이브리드 라우팅(Gemini Flash + Flash-Lite), 사용자당 일 10회 Rate Limit, 이미지 해시 캐싱(Phase 2) |
| 의료 데이터 개인정보 노출 | High | Low | SSE-S3 암호화, Pre-signed URL TTL 1h, IAM Role 분리, CloudTrail 로깅(권장) |
| 단일 서버 장애 시 전체 서비스 다운 | Medium | Medium | MVP는 가용성보다 빠른 검증 우선, Phase 2/3에서 MSA로 분리 |
| Spring Boot ↔ FastAPI 동기 HTTP 통신 지연 | Medium | High | OCR 동기 처리(MVP), Phase 2에서 Redis Stream 비동기 큐 도입 |

---

## 6. Architecture Considerations

### 6.1 Project Level Selection

| Level | Characteristics | Recommended For | Selected |
|-------|-----------------|-----------------|:--------:|
| **Starter** | Simple structure (`components/`, `lib/`, `types/`) | Static sites, portfolios | ☐ |
| **Dynamic** | Feature-based modules, BaaS integration | Web apps with backend, SaaS MVPs | ☐ |
| **Enterprise** | Strict layer separation, DI, microservices | High-traffic systems, complex architectures | ☑ |

> **선택 사유**: 의료 도메인(정확성/보안 필수), Java + Python 멀티 런타임, pgvector/RAG/AI 외부 API 등 복잡한 아키텍처가 필요하므로 **Enterprise 레벨**. 단, Phase 1 MVP는 *단일 서버 단순 구조*로 시작하고 Phase 3에서 MSA로 분리하는 **점진적 진화 전략**을 따른다.

### 6.2 Key Architectural Decisions

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| Main Backend | Spring Boot / Node.js / Django | **Spring Boot 3 (Java 17)** | 의료 도메인 안정성, JPA 생태계, 백엔드 포폴 어필 |
| AI Backend | FastAPI / Flask / Spring AI | **Python FastAPI** | LangChain/pgvector 생태계, 별도 서비스로 분리 |
| Database | PostgreSQL / MySQL | **PostgreSQL + pgvector** | 벡터 검색과 RDB를 한 DB에서 처리 (인프라 비용 절감) |
| Cache | Redis / Memcached | **Redis** | 식약처 API 캐싱 + Phase 2 큐 재사용 |
| Object Storage | S3 / GCS | **AWS S3** | Pre-signed URL, 라이프사이클, IAM 통합 |
| ORM | JPA / MyBatis / jOOQ | **Spring Data JPA** | 도메인 모델링 + Batch Update (복용 로그) |
| AI Model (OCR) | Gemini Vision / GPT-4o / Claude | **Gemini 2.5 Flash** | 한글+이미지 정확도, 비용 효율 |
| AI Model (Chat) | Gemini Flash / GPT-4o mini | **Gemini-2.5-flash** (테스트는 flash-lite) | 비용 최적화, RAG는 검색이 핵심 |
| Vector Search | pgvector / Pinecone / Weaviate | **pgvector** | PostgreSQL 통합, 운영 단순화 |
| RAG Framework | LangChain / LlamaIndex | **LangChain** | Hybrid Retrieval(BM25+Dense) 지원 |
| API Style | REST / GraphQL | **REST + OpenAPI** | 단순함, 처방전 업로드(멀티파트) 친화적 |
| Auth (Phase 1 후반) | JWT / Session / OAuth2 | **JWT + 카카오/구글 SSO** | 모바일 친화적, 그룹 권한 검증 용이 |
| 통신 (Spring ↔ FastAPI) | HTTP / gRPC | **HTTP (Phase 1)** | MVP 단순성, gRPC는 Phase 3 |
| Container | Docker / Podman | **Docker Compose** | 멀티 서비스 통합 실행 |
| Deployment | EC2 / ECS / Kubernetes | **EC2 t3.medium 단일** | MVP 비용 효율, Phase 3에서 확장 |

### 6.3 Clean Architecture Approach

```
Selected Level: Enterprise (Phase 1은 단순 구조로 시작)

Phase 1 MVP 폴더 구조:
┌─────────────────────────────────────────────────────┐
│ Spring Boot (단일 서비스 - 모듈 분리만)             │
│   src/main/java/com/pillmate/                       │
│     ├─ user/         (회원/그룹)                    │
│     ├─ drug/         (약 마스터/식약처)             │
│     ├─ prescription/ (처방전 업로드)                │
│     ├─ schedule/     (복약 스케줄)                  │
│     ├─ doselog/      (복용 체크/히스토리)           │
│     └─ common/       (공통 유틸/예외/설정)          │
│                                                     │
│ FastAPI (AI 서버)                                   │
│   app/                                              │
│     ├─ ocr/          (Gemini Vision)                │
│     ├─ rag/          (LangChain + pgvector)         │
│     ├─ chat/         (챗봇)                         │
│     ├─ recommend/    (건강 추천)                    │
│     └─ core/         (LLM 라우팅/캐시)              │
└─────────────────────────────────────────────────────┘

Phase 3 MSA 분리 시:
   User Service / Drug Service / AI Service (gRPC + Proto)
```

### 6.4 시스템 아키텍처 (Phase 1)

```
┌─────────────────────────────┐
│   Spring Boot (단일)        │
│   - User / Group            │
│   - Drug / Prescription     │
│   - Schedule / DoseLog      │
│   - 식약처 API 연동         │
└────────────┬────────────────┘
             │ HTTP
┌────────────▼────────────────┐
│   FastAPI                   │
│   - 처방전 OCR (Gemini)     │
│   - RAG 챗봇                │
│   - 건강 추천               │
└─────────────────────────────┘
             │
    ┌────────┴─────────┐
    ▼                  ▼
PostgreSQL           Redis
+ pgvector         (캐싱)
    ▲
    │
    S3 (처방전 이미지, Pre-signed URL)
```

---

## 7. Convention Prerequisites

### 7.1 Existing Project Conventions

- [ ] `CLAUDE.md` 작성 필요
- [ ] `docs/01-plan/conventions.md` 작성 필요 (Phase 2 output)
- [ ] Spring Boot: Checkstyle / Google Java Style
- [ ] Python: Black + isort + ruff
- [ ] TypeScript 없음 (백엔드 위주)

### 7.2 Conventions to Define/Verify

| Category | Current State | To Define | Priority |
|----------|---------------|-----------|:--------:|
| **패키지 네이밍** | missing | `com.pillmate.{domain}` 도메인 기반 | High |
| **API 네이밍** | missing | `/api/v1/{resource}` REST 컨벤션 | High |
| **DB 네이밍** | missing | snake_case 테이블/컬럼, 복수형 테이블명 | High |
| **에러 처리** | missing | Spring `@ControllerAdvice`, 표준 오류 응답 포맷 | Medium |
| **로그 포맷** | missing | JSON 구조화 로그 (Phase 2 모니터링 대비) | Medium |
| **환경 변수** | missing | `.env` + Spring `application-{profile}.yml` 분리 | High |

### 7.3 Environment Variables Needed

| Variable | Purpose | Scope | To Be Created |
|----------|---------|-------|:-------------:|
| `SPRING_DATASOURCE_URL` | PostgreSQL 연결 | Server | ☑ |
| `SPRING_DATASOURCE_USERNAME` | DB User | Server | ☑ |
| `SPRING_DATASOURCE_PASSWORD` | DB Password | Server | ☑ |
| `REDIS_HOST` / `REDIS_PORT` | Redis 캐시 | Server | ☑ |
| `AWS_REGION` / `AWS_S3_BUCKET` | S3 처방전 저장 | Server | ☑ |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | S3 인증 (IAM Role 권장) | Server | ☑ |
| `MFDS_API_KEY` | 식약처 API 인증키 | Server | ☑ |
| `GEMINI_API_KEY` | Gemini Vision/Chat | Server (FastAPI) | ☑ |
| `JWT_SECRET` | JWT 서명 키 (Phase 1 후반) | Server | ☑ |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | 카카오 SSO | Server | ☑ |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 구글 SSO | Server | ☑ |
| `FASTAPI_BASE_URL` | Spring → FastAPI 호출 | Server | ☑ |
| `LLM_RATE_LIMIT_PER_USER_PER_DAY` | LLM 비용 캡 (기본 10) | Server | ☑ |

### 7.4 Pipeline Integration

| Phase | Status | Document Location | Command |
|-------|:------:|-------------------|---------|
| Phase 1 (Schema/ERD) | ☐ | `docs/01-plan/schema.md` | `/pipeline-next` |
| Phase 2 (Convention) | ☐ | `docs/01-plan/conventions.md` | `/pipeline-next` |
| Phase 4 (API 명세) | ☐ | `docs/04-api/` | OpenAPI/Swagger |

---

## 8. 구현 우선순위 (Phase 1)

| 순서 | 기능 | 핵심 기술 | 예상 기간 |
|------|------|-----------|----------|
| 1 | 더미 유저 + 임시 userId (인증 우회) | Spring Security Anonymous + 임시 헤더 | 0.5d |
| 2 | 케어 그룹 (생성/초대/참여) | JPA, 초대 코드 생성, QR 코드 라이브러리 | 2d |
| 3 | S3 처방전 사진 업로드 | S3 Pre-signed URL, IAM Role | 1d |
| 4 | 처방전 OCR + 약 등록 | Gemini Vision + 식약처 매칭 + pgvector 유사도 | 4d |
| 5 | 복약 스케줄 + 복용 체크 | JPA, @Scheduled, Batch Update | 3d |
| 6 | RAG 챗봇 (Gemini-2.5-flash) | pgvector + LangChain + Hybrid Retrieval | 4d |
| 7 | 건강 추천 (Gemini-2.5-flash) | LLM + RAG (의료 가이드라인) | 2d |
| 8 | 회원가입/로그인 (카카오, 구글 SSO) | JWT + OAuth2 클라이언트 | 2d |
| 9 | Docker Compose 통합 + EC2 배포 | Docker, AWS EC2 | 2d |

**총 예상 기간**: 약 3~4주 (Notion 문서와 동일)

---

## 9. Phase 2~4 미래 계획 (요약)

> 본 Plan은 Phase 1 MVP에 집중. 아래는 향후 진화 방향 요약.

| Phase | 핵심 변화 | 트리거 |
|-------|----------|--------|
| **Phase 2: 운영 안정성** | Circuit Breaker, Redis Stream 비동기 OCR, 이미지 해시 캐싱, Hybrid Retrieval 본격 적용, Prometheus + Grafana | MVP 운영 중 발견된 구체적 문제 |
| **Phase 3: MSA 분리** | User/Drug/AI Service 분리, gRPC + Protobuf, DB per Service, OpenTelemetry | AI 서버 장애가 약 등록까지 죽이는 일 반복 |
| **Phase 4: 알림 + 출시** | WebFlux + SSE 그룹 fan-out, Kafka + Outbox Pattern, FCM, Idempotency, DLQ | 의료 알림 누락 방지 필요성 |

---

## 10. Next Steps

1. [ ] 본 Plan 검토 후 승인
2. [ ] `/pdca design PillMate-Phase1`로 설계 문서 작성
   - ERD (User, CareGroup, Prescription, Drug, Schedule, DoseLog)
   - API 명세서 (OpenAPI)
   - 시퀀스 다이어그램 (처방전 OCR 플로우 등)
   - Vector DB 청크 전략
3. [ ] 식약처 API 키 발급 신청
4. [ ] Gemini API 키 발급 + 사용량 알림 설정
5. [ ] AWS 계정 + IAM Role + S3 버킷 사전 준비
6. [ ] `/pdca do PillMate-Phase1`로 구현 시작

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-05-21 | Notion 설계 문서 기반 초안 작성 | 최경영 |
