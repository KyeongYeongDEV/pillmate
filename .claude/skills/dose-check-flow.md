---
name: dose-check-flow
description: 복용 체크 흐름(스케줄 → 체크 → 미복용 알림)을 검증하는 워크플로우.
---

# Dose Check Flow

## 흐름

```
[Scheduler @08:00, 12:30, 18:30, 22:00]
   ↓ PENDING DoseLog 생성
[환자/보호자]
   ↓ 복용 체크 (TAKEN/SKIPPED)
[Spring Boot]
   ↓ DoseLog 상태 전이 + DoseTaken 이벤트
[Phase 4: 알림 발송]
```

## 상태 머신

```
PENDING ──(체크)──→ TAKEN
   │
   ├──(체크)──→ SKIPPED (사유 기록)
   │
   └──(30분 경과)──→ DELAYED ──(체크)──→ TAKEN/SKIPPED
                       │
                       └──(2시간 경과)──→ MISSED (알림)
```

## TDD 테스트 케이스

| 테스트 | 검증 |
|--------|------|
| `PENDING → TAKEN` | 정상 전이, checked_at 기록 |
| `PENDING → SKIPPED` | 사유 필수 |
| `TAKEN → TAKEN` | 멱등 (재호출 무시) |
| `TAKEN → SKIPPED` | 거부 (불가능 전이) |
| `30분 경과 자동 DELAYED` | 스케줄 작업 검증 |
| `보호자가 환자 대신 체크` | 권한 검증 |
| `타 그룹 약 체크 시도` | 거부 |

## 성능 검증

- [ ] 1만 명 × 일 3회 가정 → 월 900만 DoseLog
- [ ] 파티션 (월 단위) 적용
- [ ] 월별 복용률 쿼리 < 100ms
- [ ] DoseLog Batch Insert (100건 단위)

## 참조

- `agents/dose-schedule-engineer.md`: 스케줄 엔지니어
- `rules/sql/postgres.md`: 파티션/인덱스 규칙
