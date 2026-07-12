# PillMate — Claude Code Harness

> 보호자 ↔ 노인/환자 그룹 기반 스마트 복약 관리 플랫폼
>
> **포지셔닝**: 의료 도메인의 정확성 + 점진적 시스템 진화 + RAG 깊이

이 파일은 Claude Code(및 호환 AI 에이전트)에게 PillMate 프로젝트의
**불변 규칙**과 **하네스 사용법**을 알리는 진입 가이드입니다.

---

## 🚨 절대 규칙 (Non-negotiable)

1. **TDD**: 모든 도메인/유스케이스 코드는 RED → GREEN → REFACTOR로 작성
2. **DDD 레이어드**: `presentation → application → domain ← infrastructure` 의존성 역전 절대 금지
3. **의료 안전**: 출처 없는 의료 정보 응답 금지, 식약처 DB 검증 필수
4. **오버엔지니어링 금지**: Phase 1은 단일 서버, MSA/Kafka는 Phase 3/4
5. **Ubiquitous Language**: `.claude/contexts/ubiquitous-language.md`의 용어만 사용
6. **DB 데이터 삭제 절대 금지** (2026-05-25 사용자 명시): 어떤 에이전트도(CTO·BE·FE·QA-Claude·QA-Gemini) `DELETE/TRUNCATE/DROP/UPDATE(WHERE 없는)` 절대 X. 사용자 명시 동의 + spec 명시한 경우만 예외. 상세 `.claude/rules/common/db-safety.md`
7. **시크릿 노출 금지** (2026-07-07): 시크릿 값을 채팅·로그·커밋·명령줄 인자에 출력 절대 X. compose 검증은 더미 `--env-file` 필수. 상세 `.claude/rules/common/secret-safety.md`
8. **실측 증거 없는 DONE 금지** (2026-07-07): UI=스크린샷·픽셀실측, API=curl 실측, 버그=재현→픽스→재현불가. 코드 diff 만으로 DONE 반려. 상세 `.claude/rules/common/verification-evidence.md`
9. **QA 리스크 2단계** (2026-07-07): 의료·보안·데이터·결제·배포·개인정보 = 트리오(Reviewer+QA-Claude+Adversarial) 의무, 그 외 = CTO 직접검증. 상세 `.claude/rules/common/qa-risk-tiers.md`
10. **스코프 규율** (2026-07-07): spec 명시분만 변경. 무관 코드·동작 건드림 금지, 없앤 제약/기능 spec 없이 재추가 금지(anti-revival), 같은 파일 동시편집 금지. 상세 `.claude/rules/common/scope-discipline.md`

위반 시 PR 차단. 자세한 규칙은 `.claude/rules/`를 참조.

---

## 🏗 프로젝트 구조

```
pillmate/                    # 루트 (monorepo)
├── back/                    # 서버 관련 (BE)
│   ├── app_server/          # Spring Boot (src/, build.gradle, gradlew, Dockerfile)
│   ├── ai_server/           # FastAPI AI 서버 (OCR / RAG / 추천 / 리포트)
│   ├── infra/               # postgres init.sql
│   ├── scripts/             # 운영 스크립트 (bulk import, 임베딩)
│   └── docker-compose.yml
├── front/                   # RN 관련 (FE)
│   └── (React Native + Expo 크로스플랫폼)
├── .claude/                 # 하네스 (Claude Code 표준 디렉토리)
│   ├── agents/              # 13개 도메인 특화 서브에이전트
│   ├── skills/              # 7개 워크플로우 스킬 (TDD, DDD, OCR, RAG ...)
│   ├── commands/            # /pill-* 슬래시 명령
│   ├── hooks/               # hooks.json (TDD/DDD 강제 훅)
│   ├── rules/               # 코딩 규칙 (common + java + python + sql)
│   ├── contexts/            # 동적 시스템 프롬프트 (의료 도메인, UL, 진화 스토리)
│   ├── mcp-configs/         # MCP 서버 설정 (Gemini, 식약처, S3, PostgreSQL)
│   ├── schemas/             # JSON 스키마 + ERD
│   └── scripts/             # 검증 스크립트 (TDD 페어, 레이어 의존, 의료 출처)
├── docs/                    # PDCA 문서 (Plan / Design / Analysis / Report)
├── CLAUDE.md
└── GEMINI.md
```

---

## 🎯 도메인 (Bounded Contexts)

| Context        | Aggregate Root            | 책임                |
| -------------- | ------------------------- | ------------------- |
| `user`         | `User`                    | 인증, 프로필        |
| `caregroup`    | `CareGroup`               | N:N 케어 그룹, 초대 |
| `prescription` | `Prescription`            | 처방전 등록, OCR    |
| `drug`         | `Drug`, `DrugInteraction` | 약 마스터, 식약처   |
| `schedule`     | `Schedule`                | 복약 시간표         |
| `doselog`      | `DoseLog`                 | 복용 체크, 히스토리 |

`back/app_server/src/main/java/com/pillmate/{context}/{presentation,application,domain,infrastructure}/`

---

## 🛠 기술 스택 (Phase 1 MVP)

| 분류         | 기술                                                |
| ------------ | --------------------------------------------------- |
| Backend      | Java 17, Spring Boot 3, Spring Data JPA             |
| AI Backend   | Python 3.11+, FastAPI, LangChain                    |
| DB           | PostgreSQL 16 + pgvector + pg_trgm                  |
| Cache        | Redis                                               |
| Storage      | AWS S3 (Pre-signed URL, SSE-S3)                     |
| AI           | Gemini 2.5 Flash / Flash-Lite                       |
| External API | 식품의약품안전처 의약품안전나라                     |
| Test         | JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit |
| Infra        | Docker Compose, AWS EC2                             |

---

## 🧪 TDD 사이클

```
1. RED      → 실패 테스트 작성, 실패 확인
2. GREEN    → 최소 코드로 통과
3. REFACTOR → 중복 제거, 명명 개선
4. 커밋     → 한 사이클 = 한 커밋
```

도구: `/pill-tdd {context} "동작 설명"` 또는 `.claude/agents/tdd-coach.md`

---

## 📐 DDD 레이어 의존 규칙

```
presentation  →  application  →  domain
                       ↓
                infrastructure  →  domain
```

ArchUnit으로 자동 검증: `/pill-arch-check`
세부 규칙: `.claude/rules/java/ddd-layered.md`

---

## 🚀 자주 쓰는 명령

| 상황                    | 명령                                        |
| ----------------------- | ------------------------------------------- |
| TDD 사이클 시작         | `/pill-tdd {context} "동작"`                |
| 새 Bounded Context 생성 | `/pill-ddd-new {context} "설명"`            |
| OCR 흐름 검증           | `/pill-ocr-test`                            |
| RAG 정확도 평가         | `/pill-rag-eval`                            |
| 비용 감사               | `/pill-cost`                                |
| 레이어 의존 검증        | `/pill-arch-check`                          |
| PDCA 진행               | `/pdca {plan\|design\|do\|analyze\|report}` |

---

## 🤖 에이전트 사용 가이드

| 상황                  | 에이전트                   |
| --------------------- | -------------------------- |
| 도메인 모델 설계      | `ddd-modeler`              |
| 테스트 우선 코드 작성 | `tdd-coach`                |
| Spring Boot 모듈 설계 | `spring-boot-architect`    |
| AI 서버 구현          | `fastapi-ai-engineer`      |
| 처방전 OCR            | `prescription-ocr-expert`  |
| RAG 인덱스 관리       | `rag-curator`              |
| 의료 응답 검증        | `medical-domain-validator` |
| 그룹 권한 설계        | `care-group-modeler`       |
| 복약 스케줄           | `dose-schedule-engineer`   |
| 식약처 API            | `drug-data-broker`         |
| S3 처방전             | `s3-prescription-handler`  |
| MSA 분리 시점         | `msa-evolution-strategist` |
| 비용 최적화           | `cost-optimizer`           |

상세: `agents/README.md`

---

## 🔄 Phase 로드맵

| Phase         | 목표        | 주요 추가 기술                                  |
| ------------- | ----------- | ----------------------------------------------- |
| **1 (3~4주)** | MVP 출시    | Spring Boot + FastAPI + PG + Redis + S3         |
| **2**         | 운영 안정성 | Circuit Breaker, Redis Stream, Hybrid Retrieval |
| **3**         | MSA 분리    | gRPC, OpenTelemetry, DB per Service             |
| **4 (출시)**  | 알림 시스템 | WebFlux + SSE, Kafka + Outbox, FCM              |

**오버엔지니어링 금지**: 각 Phase는 이전 Phase에서 발견된 **구체적 운영 문제**가
근거. 자세한 narrative는 `contexts/evolution-story.md`.

---

## 💰 비용 목표

- **Phase 1 운영**: 월 ~$80 (≈ ₩112,000)
- **LLM 비용 절감**: GPT-4o 단독 대비 92% (월 $11)
- **전략**: 하이브리드 모델 라우팅 + 이미지 해시 캐싱 + RAG FAQ 캐싱

상세: `.claude/agents/cost-optimizer.md`, `.claude/skills/cost-audit.md`

---

## 🛡 의료 데이터 보호

- 처방전 이미지: S3 SSE-S3 암호화, 3년 후 자동 삭제
- 그룹 격리: 모든 query에 `group_id` 필터 강제
- 감사 로그: 의료 데이터 접근 3년 보관
- LLM 응답: 식약처 출처 강제, 환각 시 차단

상세: `.claude/rules/common/medical-safety.md`, `.claude/contexts/medical-domain.md`

---


---

## ✅ 품질 기준선 (체크 가능한 기준 — 형용사 금지)

> 모든 모델(패널·서브에이전트)은 작업 전 이 레이어를 먼저 읽는다. "잘", "깔끔히" 같은 형용사가 아니라 체크 가능한 기준.

| # | 기준 (체크 가능) | 판정 |
|---|-----------------|------|
| Q1 | DONE 보고에 실측 증거 첨부 (UI=스크린샷/픽셀, API=curl, 버그=재현3단) | 없으면 반려 |
| Q2 | 변경 파일이 spec 명시 범위 안인가 (`git diff --stat` 로 확인) | 벗어나면 반려 |
| Q3 | 새 제약/검증 추가 시 과거 제거 이력 grep/git log 확인했는가 | 미확인이면 반려 |
| Q4 | 시크릿 값이 출력/커밋에 0건인가 | 1건이라도 있으면 즉시 중단 |
| Q5 | 도메인 코드에 대응 테스트 존재 + GREEN | 없으면 반려 |
| Q6 | 트랜잭션 안 외부호출 0, DDD 역방향 의존 0 | 위반 즉시 수정 |

### 약한 모델이 자주 저지르는 실수 (이 프로젝트 실사고 기반)
1. 코드 diff 만 보고 "적용됐다" 판정 (렌더/컨테이너 미반영) → Q1 로 차단
2. "이왕 하는 김에" 무관 코드 정리 → Q2 로 차단
3. 좋아 보이는 검증·제약을 임의 추가 (사용자가 없앤 것 재추가) → Q3 로 차단
4. compose/printenv 출력에 실키 노출 → Q4 로 차단
5. 실패를 DONE 으로 포장 → 검증 불가면 "구현 완료, 검증 블록: <이유>" 로 정직 보고

### Escalation 기준 (불확실할 때)
- spec 에 없는 결정이 필요하다 → **CTO 에 질문** (추측 진행 금지)
- 두 룰이 충돌한다 → 우선순위: medical > db > secret > ddd > tdd > 나머지
- 같은 접근 2회 실패 → 접근을 바꾸지 말고 **일단 보고** (3회째 시도 전에 CTO 판단)
- 사용자 데이터를 변경/삭제해야 할 것 같다 → 무조건 중단 + 보고 (db-safety)

## 📚 더 읽을 거리

- `docs/01-plan/features/PillMate-Phase1.plan.md` — Phase 1 계획서
- `.claude/agents/README.md` — 에이전트 카탈로그
- `.claude/skills/README.md` — 스킬 카탈로그
- `.claude/rules/README.md` — 코딩 규칙 인덱스
- `.claude/contexts/evolution-story.md` — 면접/포트폴리오 narrative

---

## 📝 변경 이력

| 날짜       | 변경                                          |
| ---------- | --------------------------------------------- |
| 2026-05-21 | 하네스 골격 초기 작성 (TDD + DDD + 의료 안전) |
| 2026-07-07 | 하네스 강화: secret-safety·verification-evidence·qa-risk-tiers 룰, deploy-gate 스킬, Researcher→Ops 패널 전환, say 오배달 가드, secret-leak 훅, bkit 플러그인 제거 |
