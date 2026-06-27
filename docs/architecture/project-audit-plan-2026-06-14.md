# PillMate 전체 구현 점검 계획 (2026-06-14)

> 배경: OCR/매칭이 "구현됐다 표시됐지만 운영 경로는 deprecated DrugMatcher였고 평가는 3분기로 갈려 있던" 사태(#150~#153)를 겪음.
> 같은 패턴("구현 ≠ 운영 와이어링 ≠ 평가")이 다른 영역에도 있는지 **순서대로 전수 점검**한다.

## 🎯 집중 점검 7영역 (사용자 지정 2026-06-14)

> 아래 7개를 최우선으로 점검. 나머지(report/별칭/인프라)는 후순위.
> 예비 스캔(2026-06-14)으로 이미 격차 의심 발견 — ⛔ 표시.

| # | 영역 | 매핑 | 예비 스캔 결과 |
|---|------|------|---------------|
| 1 | **OCR** | ai_server/ocr | ✅ #150~#153 완료·검증됨 |
| 2 | **RAG** | ocr 매칭 retrieval(pgvector+어댑터) | ⚠️ OCR에 통합됨 — 검색 품질/와이어링 독립 점검 필요 |
| 3 | **그룹 관리** | caregroup (생성/초대/QR/핀/멤버) | 엔드포인트 존재 — group_id 격리·권한 검증 필요 |
| 4 | **그룹 복약 알림** | notification (그룹발행+폴러+푸시) | 동작 확인됨, 단 실푸시는 log stub (provider=expo 미전환) |
| 5 | **처방전 목록** | prescription | ⛔ **격차 의심 高 — BE 목록 GET 없음, FE 35줄 placeholder(데이터 fetch 0)** |
| 6 | **처방전 자세히 보기** | prescription | ⚠️ candidates/resolve(OCR결과 수정)만, **전체 상세 GET 없음** |
| 7 | **약 자세히 보기** | drug | GET /drugs/{kdCode} + FE drug/[kdCode].tsx 존재 — 와이어링 검증 필요 |

### 점검 순서 (위 7영역 기준)
- **A. OCR/RAG** (#1, #2): OCR 완료. RAG 검색 레이어만 추가 점검.
- **B. 처방전 묶음** (#5, #6): 격차 가장 큼 — 목록/상세가 실제 있는가부터. **최우선 의심**.
- **C. 그룹 묶음** (#3, #4): 격리·권한 + 알림 실경로.
- **D. 약 상세** (#7): 검색→상세 와이어링.

---

## 점검 4원칙 (모든 영역 공통 — OCR 게이트 방식 재사용)
각 기능마다 4가지를 확인:
1. **와이어링**: main/DI/라우터가 실제로 그 코드를 호출하나 (deprecated/미연결 아닌가)
2. **평가=운영**: 테스트/평가가 실제 운영 경로를 검증하나 (별도 시뮬 경로 아닌가)
3. **의도 vs 구현**: Plan 문서·룰(`.claude/rules`)대로인가 (예: langchain.md hybrid retrieval)
4. **안전망**: 의료 안전(출처·병용금기·신뢰도)·DB 안전(삭제 금지) 지켜지나

---

## 구현 현황 인벤토리

### Backend (Spring Boot, 10 Bounded Context)
| Context | 주요 구현 | 엔드포인트 | 점검 우선 |
|---------|----------|-----------|----------|
| user | 인증 **미완**(X-User-Id 하드코딩) | /users/me, /device-token | **P0 (#99 블로커)** |
| caregroup | 그룹/초대/QR/핀/가입 | /groups/* | P1 |
| prescription | OCR 업로드/candidates/resolve | /prescriptions/* | P1 |
| drug | 검색 + **별칭(FE 미연결 의심)** | /drugs/*, /drugs/aliases/* | P2 |
| schedule | day/month 집계 | /schedules/day, /month | P1 |
| doselog | check/오늘만/60초/취소 | /dose-logs/check, /notify-group | **P0 (의료)** |
| notification | 그룹발행/Expo푸시/10초폴러 | /notifications/* | **P0 (의료)** |
| activity | 피드 broadcast | /activity | P2 |
| report | weekly/monthly/daily/insights | /reports/* | P2 (stub 의심) |
| common | 보안/예외/응답 | — | P1 |

### AI server (FastAPI)
| 모듈 | 구현 | 상태 |
|------|------|------|
| ocr | RrfMatcher 운영 통합 완료 | ✅ #150~#153 검증됨 |
| chat (RAG 챗봇) | `chain.py` 단일 DrugRetriever | ❌ **점검 제외** — 출시에 미포함 결정(2026-06-14). FE 잠금 유지(CHAT_ENABLED=false). 향후 출시 시 의료안전 P0로 재편입 |
| analyze (건강 리포트) | health_report 모듈 | P2 미검증 |

### Frontend (RN/Expo)
| 화면 | 비고 |
|------|------|
| home/schedule | 달력·복약·스트릭 (#135~#147 검증됨) |
| group(s) | 그룹/초대/QR |
| prescriptions | 업로드→OCR→결과→수정 |
| drugs | 약 검색 (별칭 검수 UI **없음 의심**) |
| chat | **점검 제외** (출시 미포함, 잠금 유지) |
| my | 설정 |

### 마이그레이션 V1~V27 (V9, V19 결번 확인 필요)

---

## 단계별 점검 계획 (우선순위 = 의료안전 > 출시블로커 > 핵심기능 > 보조)

### Stage 1 — P0 의료 안전 경로 (최우선)
- **1a. doselog**: check(TAKE/SKIP/CANCEL) 상태전이, 오늘만(isEditableOn KST), 60초 서버폴러, 취소 알림. 운영 와이어링 + 통합테스트 실재 확인.
- **1b. notification**: 그룹 발행 → Expo 푸시(provider=log/expo flag) → 10초 폴러 recency. 실 발송 경로(현재 log stub) 명시.
- **1c. DDI 병용금기(#100)**: 처방 등록 시 약쌍 검증이 **실제 호출되나**, 식약처 출처 강제되나. ← OCR처럼 "구현됐는데 미연결" 가능성 1순위.
- **1d. 의료 fallback**: OCR 신뢰도<0.7→검수, RAG faithfulness<0.95→차단 이 실제 동작하나.

### Stage 2 — P0 출시 블로커
- **2a. user 인증(#99)**: 현재 X-User-Id 하드코딩 전수 파악 → JWT 설계. (점검이 아니라 구현 필요)
- **2b. 패키지명/빌드**: com.anonymous.client → 정식, EAS 빌드 가능 여부.

### Stage 3 — P1 핵심 기능 와이어링 + 평가=운영
- **3a. 처방 플로우 E2E**: 업로드URL→S3→OCR→candidates→resolve→schedule 생성까지 **끊김 없이 연결**되나 (FE↔BE↔AI 3자).
- **3b. schedule↔doselog↔home 단일 진실 소스(#84)**: 실제 한 소스인가.
- **3c. caregroup 권한**: group_id 필터가 모든 쿼리에 강제되나(격리), QR/초대 Redis TTL.

### Stage 4 — P2 보조 기능 (의심 2종)
- **4a. report insights**: weekly/insights가 실 집계/LLM인가 stub인가.
- **4b. drug alias**: BE 서비스 존재하나 FE 미연결 — dead path 확정 + 연결 or 제거 결정.

> **점검 제외 (2026-06-14 사용자 결정)**: chat RAG 챗봇 — 현재 출시에 미포함. FE 잠금(CHAT_ENABLED=false) 유지. 향후 출시 결정 시 의료안전 P0(출처강제·faithfulness 0.95·hybrid retrieval)로 Stage 1에 재편입.

### Stage 5 — 인프라/데이터
- **5a. 마이그레이션 정합**: V1~V27 순서, 결번, dev seed 분리.
- **5b. 비용 가드**: LLM 캐시(이미지 해시/FAQ), 일 10회 제한, Redis TTL 강제.

---

## 점검 방식 (각 Stage)
1. 코드 정적 점검 (와이어링 grep + DI 확인) → researcher/CTO read-only
2. "구현 vs 의도" 차이 발견 시 게이트 방식(측정→보고→승인→수정)
3. 발견은 전부 이 문서에 누적, 수정은 BE/FE 디스패치
4. 각 Stage 끝에서 사용자 보고 → 다음 Stage 승인

## 진행 로그
- 2026-06-14: 계획 수립. OCR(Stage 관련)은 #150~#153로 선완료.
- 2026-06-14: **Stage B(처방전 목록·상세 #5,#6) 정적 점검 완료** — 결과 아래.

### [점검결과] 처방전 목록(#5) · 자세히 보기(#6) — 부분 미구현 (read API + FE 미완)
**저장은 됨**: Prescription 엔티티에 imageKey/prescribedAt/ocrStatus/drugs[]/candidates[]/patientId/careGroupId 전부 저장. POST /ocr 로 등록됨.
**Repository도 있음**: `findAllByPatientId(patientId)` + `findById(id)` 둘 다 구현돼 있음 (데이터 접근 레이어 완비).
**그런데 막혀 있음**:
- ❌ GET /prescriptions (목록) — UseCase·엔드포인트 없음
- ❌ GET /prescriptions/{id} (상세) — UseCase·엔드포인트 없음 (findById 있는데 미노출)
- ⚠️ GET /{id}/candidates 는 OCR 미해결 후보만 (상세 아님)
- ❌ FE `prescriptions.tsx` = 35줄 placeholder, "등록된 처방전이 아직 없습니다" **하드코딩**, API 호출 0건

**판정**: OCR 매처보다 가벼운 격차 — 도메인·repository는 완비, **read 엔드포인트 2개 + FE 목록/상세 화면만 미구현**. "처방전 등록은 되는데 다시 못 본다".
**수정 범위**: BE GetPrescriptionsUseCase(목록) + GetPrescriptionDetailUseCase(상세) + 컨트롤러 2개(본인 격리 UserContext) / FE 목록 query + 상세 화면 + prescriptions.tsx 실데이터 연결.

### [점검결과] 7영역 전수 (2026-06-14) — 격차 지도
| # | 영역 | BE | FE | 판정 |
|---|------|----|----|------|
| 1 | OCR | ✅ | ✅ | 완료 (#150~153) |
| 2 | RAG 검색 | ⚠️ hybrid 룰 미충족(OCR RRF엔 pgvector 빠짐, Chat은 dense단독) | — | 룰↔구현 격차. OCR 98%라 기능문제 X. Chat은 출시제외. VectorMultiAdapter dead code |
| 3 | 그룹 관리 | ✅ UseCase 7 + Membership 격리 | ✅ 화면/훅 완전 | 양호 (list endpoint만 재확인) |
| 4 | 그룹 복약 알림 | ✅ 트리거4종+60초폴러 발행→리스너→발송 | ❌ **알림함 화면 없음 + 읽음처리 미호출** | BE완비, FE 인앱 알림목록 미구현. provider 기본 log(운영 expo 전환 필요) |
| 5 | 처방전 목록 | ❌ 목록 GET 없음(repo findAllByPatientId는 있음) | ❌ 35줄 placeholder | read API + FE 미구현 |
| 6 | 처방전 상세 | ❌ 상세 GET 없음(findById는 있음) | ❌ 없음 | read API + FE 미구현 |
| 7 | 약 자세히 보기 | ✅ GET /{kdCode}+search 완전 동작 | ❌ **전부 MOCK** ([kdCode].tsx MOCK_DRUGS, drugs탭 "Phase2예정", 검색 MOCK) | BE완비, FE MOCK 갇힘 + 응답필드 불일치(BE TEXT vs FE 배열) |

### 공통 패턴 (OCR과 다름)
- OCR은 "깊은 아키텍처 분기"였으나, 나머지는 **"BE는 거의 완비인데 FE가 MOCK/placeholder로 멈춤 + 일부 BE read 엔드포인트 누락"**.
- 즉 수정이 **가볍고 명확**: ①BE read 엔드포인트 2~3개 추가 ②FE MOCK→실연결 ③FE 신규 화면 몇 개 ④DTO 필드 정렬.

### 추가 발견 (정리 대상)
- alias: FE `/drugs/alias`(단수) ↔ BE `/drugs/aliases`(복수) URL 불일치, FE는 로깅만(검색/검수 UI 없음)
- notify-group 수동 엔드포인트 FE 미사용(폴러로 대체됨 — 정상, 제거 검토)
- RAG VectorMultiAdapter / PgVectorDrugSearch(legacy) 미사용 dead code

---

## 수정 계획 (우선순위 — 핵심 UX > 정합/정리)

### Fix-1 (P0 핵심 UX): 약 자세히 보기 FE 실연결 (#7)
BE 완비라 FE만: drugApi MOCK→실 fetch 전환(getDrugDetail/searchDrugs), drug/[kdCode].tsx 실데이터, drugs 탭 검색 UI 구현. **BE DTO와 FE 타입 정렬**(efficacy/dosage/sideEffect를 배열 기대→TEXT 단일로 맞추거나 BE를 구조화). 가장 ROI 높음(BE 0, FE만).

### Fix-2 (P0 핵심 UX): 처방전 목록·상세 (#5,#6)
BE read 2개(GetPrescriptions/GetPrescriptionDetail + 컨트롤러, UserContext 격리) + FE 목록 query/상세 화면/prescriptions.tsx 실연결.

### Fix-3 (P1): 인앱 알림함 (#4 FE)
notificationApi 신규 + 알림 목록 화면 + 읽음 처리(PATCH read) 연결. (푸시는 이미 됨, 인앱 목록만)

### Fix-4 (P2 정합/정리): RAG 룰 정합(#2) + alias URL/기능 + dead code 제거
RAG: 룰을 현 구현에 맞게 개정 OR pgvector를 OCR RRF에 추가(택1). alias URL 통일. 미사용 어댑터 정리.

### 비고
- provider=log → expo 전환은 FCM 작업(별도)과 함께.
- #99 인증은 이 전체와 독립된 출시 블로커(병행).
