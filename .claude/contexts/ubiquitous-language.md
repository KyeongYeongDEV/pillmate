---
name: ubiquitous-language
description: PillMate Ubiquitous Language — 도메인 용어 사전
---

# Ubiquitous Language

PillMate 코드/문서/대화에서 사용하는 표준 도메인 용어입니다.
**동의어 사용 금지** — 코드와 문서, 대화 모두 동일 용어를 사용합니다.

## 사용자 도메인

| 한국어 | 영어 (코드) | 정의 |
|--------|------------|------|
| 사용자 | User | 시스템에 가입한 개인 |
| 환자 | Patient | 약을 복용하는 당사자 (역할) |
| 보호자 | Guardian | 환자 케어를 돕는 가족 (역할) |
| 관리자 | Admin | 그룹 관리자 (역할) |

## 그룹 도메인

| 한국어 | 영어 (코드) | 정의 |
|--------|------------|------|
| 케어 그룹 | CareGroup | 보호자 + 환자가 함께하는 단위 |
| 멤버십 | Membership | 사용자의 그룹 참여 + 역할 |
| 초대 코드 | InviteCode | 6자리 영숫자, TTL 24h |
| QR 코드 | InviteQrCode | 초대 코드를 임베딩한 QR |

## 처방/약 도메인

| 한국어 | 영어 (코드) | 정의 |
|--------|------------|------|
| 처방전 | Prescription | 의사 처방 1건 (이미지 + 추출 결과) |
| 처방 약 | PrescribedDrug | 처방전에 포함된 약 1개 |
| 약 마스터 | Drug | 식약처 등록된 약품 정보 |
| 약품 코드 | DrugCode | 식약처 의약품 코드 |
| 성분 | Ingredient | 약의 활성 성분 |
| 효능 | Efficacy | 약의 효과 |
| 부작용 | SideEffect | 약 복용 시 부작용 |
| 병용금기 | DrugInteraction | 함께 복용 금지 조합 |

## 복약 도메인

| 한국어 | 영어 (코드) | 정의 |
|--------|------------|------|
| 복약 스케줄 | Schedule | 약 + 시간 + 기간의 조합 |
| 복용 시간대 | TimeOfDay | MORNING/NOON/EVENING/BEDTIME |
| 복용 시간 | DoseTime | 구체 시각 (08:00 등) |
| 복용 빈도 | Frequency | 1일 N회 + 간격 |
| 복용 체크 | DoseCheck | 1회 복용 행위 |
| 복용 로그 | DoseLog | 복용 체크 기록 |
| 미복용 | Missed | 시간 경과 후 체크 없음 |
| 스킵 | Skipped | 의도적으로 복용 안 함 |

## AI 도메인

| 한국어 | 영어 (코드) | 정의 |
|--------|------------|------|
| 처방전 인식 | PrescriptionOcr | 이미지 → 약 리스트 추출 |
| 복약 상담 | MedicationConsultation | RAG 챗봇 1회 대화 |
| 건강 추천 | HealthRecommendation | 처방 기반 식단/운동 추천 |
| 복약 리포트 | DoseReport | 월간 복용 분석 |
| 신뢰도 | Confidence | OCR/RAG 결과의 확신도 (0~1) |

## 동의어 금지 예

| 사용 ✅ | 사용 금지 ❌ |
|---------|-------------|
| Prescription | Rx, Order, Recipe |
| CareGroup | Family, Team |
| Guardian | Parent, Carer |
| DoseLog | Intake, Record |
| MFDS | KFDA, KoreanFDA |

## 일관성 검증

- PR 리뷰 시 용어 일치 확인
- 새 용어 추가 시 이 문서 업데이트 필수
- 영어 ↔ 한국어 매핑은 1:1 유지

## 참조

- `agents/ddd-modeler.md`
- `rules/java/spring-boot.md`
