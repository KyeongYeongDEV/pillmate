# Spec — 처방전(약봉투) 목록 카드 리디자인 + 처방 라벨/메모

> 사용자 요청(2026-06-24): 목록 카드에 ①복약 기간 남은 정도 ②어떤 처방인지(=사용자 라벨) ③간단 메모 표시.
> 결정: 병원·의사·처방이름은 미저장 → 지어내지 않음(medical-safety). 대신 **사용자가 등록 시 라벨 입력** + 메모 영속화.
> 데이터 현황: `Schedule.startDate/endDate`·`PrescribedDrug.durationDays` 존재 → 기간/D-day 산출 가능. `DoseLog.TAKEN` → 복약률. memo는 등록 UI엔 있으나 BE 미저장.

## 공통: PrescriptionSummary 확장 (계약)
기존: `{ id, prescribedAt, drugCount, drugNames }`
추가:
```
{ id, prescribedAt,
  label: string|null,          // 사용자 입력 처방 라벨 (예: "내과 진료 처방")
  memo: string|null,           // 영속화된 메모 (간단 표시)
  drugCount, drugNames,
  status: "ONGOING"|"COMPLETED",   // endDate vs 오늘 (중단 상태는 미모델 → 제외)
  periodStart: date|null,      // 해당 처방 schedules min(startDate)
  periodEnd: date|null,        // max(endDate)
  daysRemaining: int|null,     // periodEnd - 오늘 (ONGOING만; D-day). 음수면 0
  progressRate: number,        // 0..1 (경과/전체 기간)
  adherenceRate: number|null   // 0..1 (해당 처방 schedules의 DoseLog TAKEN/total)
}
```

## BE 작업 (T-BE-RX-SUMMARY)
### B1. 마이그레이션 V32 (additive only — db-safety: ADD COLUMN 허용, DROP/DELETE 금지)
- `ALTER TABLE prescriptions ADD COLUMN label VARCHAR(100); ADD COLUMN memo VARCHAR(500);` (nullable)
- 기존 V1~V31 수정 금지.
### B2. 엔티티/등록
- `Prescription`: label, memo 필드 추가(nullable). create/factory에 optional 파라미터.
- 등록 UseCase/Request: label, memo optional 수신 → 영속화.
### B3. 목록 조회 확장 (PrescriptionSummary)
- label, memo 반환.
- status: periodEnd ≥ 오늘 → ONGOING, else COMPLETED.
- periodStart/End: 해당 prescription_id schedules의 min(startDate)/max(endDate). schedule 없으면 prescribedAt + max(durationDays) fallback, 그래도 없으면 null.
- daysRemaining: ONGOING이면 max(0, periodEnd - 오늘), COMPLETED면 null.
- progressRate: clamp01((오늘 - periodStart)/(periodEnd - periodStart)); COMPLETED=1.
- adherenceRate: 해당 schedules의 DoseLog 중 TAKEN/total. dose log 없으면 null.
- ★N+1 회피: schedules·doseLog 집계를 배치 쿼리(GROUP BY prescription_id)로. 트랜잭션 내 외부호출 없음.
### B4. TDD
- summary 계산 단위테스트(ONGOING/COMPLETED, D-day, progress, adherence null/값), 등록 label/memo 영속화, ArchUnit 통과. DB 삭제·변경 금지.

## FE 작업 (T-FE-RX-CARD)
### F1. 등록(review) 라벨 입력
- review 화면에 optional "처방 이름·병원" 입력칸 추가(placeholder "예: 내과 진료 처방"). 기존 '약봉투 메모' 입력은 ★BE로 전송·영속되도록 register payload에 label+memo 포함.
### F2. PrescriptionSummary 타입 확장 (위 계약대로)
### F3. PrescriptionListCard 리디자인 (목업 '처방전 목록')
- 상단: 상태 칩(복용중 green / 복용완료 gray) + prescribedAt(YYYY.MM.DD). (중단 상태 없음)
- 라벨 줄: label 있으면 굵게(예 "내과 진료 처방"); 없으면 drugNames 대표 + "외 N종"로 대체(폴백).
- 기간 줄: "{durationDays}일분 · {periodStart M.D} → {periodEnd M.D}" + 우측 D-day:
  - ONGOING: daysRemaining>1 → "D-{n}"(기본색), ==1 → "내일 마지막"(주황 statusCautionary 강조), ==0 → "오늘 마지막".
  - COMPLETED: 우측 "복약률 {adherenceRate%}".
- 진행률 바: progressRate. COMPLETED면 100%.
- 메모 박스: memo 있으면 연노랑 박스 + 펜 아이콘 + 1~2줄 ellipsis(간단히). 없으면 미표시.
- 하단: 알약 색 dots + "약 {drugCount}개" + "상세 >"(→ 상세 네비 유지).
- 토큰만/매직넘버X/주석생략, Card 하위 요소 컴포넌트 추출(clean-code).
- ★주의: 상단 '약봉투' 헤더(가운데 타이틀)·탭바는 건드리지 말 것. 확인필요 칩 제거 유지.
- (선택, 이번 범위 외) 상태 필터칩(전체/복용중/복용완료), NEW 뱃지 — 사용자 미요청 → 후속.

## 의존
- FE F3는 BE B3 계약에 의존하나, 계약 고정이므로 병렬 가능(목 데이터로 카드 먼저 가능).
