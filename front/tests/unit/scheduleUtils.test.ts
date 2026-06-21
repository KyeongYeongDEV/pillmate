import { medSlotToTimeSlot, buildDoseHeadline, deriveSlotStatuses, deriveOverlayState } from '@/lib/scheduleUtils';
import type { HeadlineSlot } from '@/lib/scheduleUtils';
import { MOCK_SCHEDULE } from '@/store/slices/scheduleApi';
import type { DoseOverrideState } from '@/store/slices/doseStateSlice';

describe('medSlotToTimeSlot', () => {
  it('MOCK_SCHEDULE.slots[0] → TimeSlot 변환 (drugCount, pillColors)', () => {
    const slot = MOCK_SCHEDULE.slots[0];
    const result = medSlotToTimeSlot(slot);
    expect(result.drugCount).toBe(2);
    expect(result.pillColors).toEqual(['#A8D4FF', '#FFAA6B']);
  });

  it('id / label / time / state / doseLogId 보존', () => {
    const slot = MOCK_SCHEDULE.slots[0];
    const result = medSlotToTimeSlot(slot);
    expect(result.id).toBe('morning');
    expect(result.label).toBe('아침');
    expect(result.time).toBe('08:00');
    expect(result.state).toBe('done');
    expect(result.doseLogId).toBe(4);
  });

  it('drugCount 없으면 items.length 폴백', () => {
    const slot = { ...MOCK_SCHEDULE.slots[0], drugCount: undefined };
    const result = medSlotToTimeSlot(slot);
    expect(result.drugCount).toBe(MOCK_SCHEDULE.slots[0].items.length);
  });

  it('pillColors 없으면 빈 배열 폴백', () => {
    const slot = { ...MOCK_SCHEDULE.slots[0], pillColors: undefined };
    const result = medSlotToTimeSlot(slot);
    expect(result.pillColors).toEqual([]);
  });

  it('now 상태 슬롯 (noon) 변환', () => {
    const slot = MOCK_SCHEDULE.slots[1];
    const result = medSlotToTimeSlot(slot);
    expect(result.state).toBe('now');
    expect(result.drugCount).toBe(3);
    expect(result.doseLogId).toBe(5);
  });

  it('홈·스케줄 동일 doseLogId 보장 — V19 SEED 매칭', () => {
    const ids = MOCK_SCHEDULE.slots.map(s => s.doseLogId);
    expect(ids).toEqual([4, 5, 6, 7]);
  });

  it('모든 슬롯 변환 후 TimeSlot 배열 — 4개', () => {
    const results = MOCK_SCHEDULE.slots.map(medSlotToTimeSlot);
    expect(results).toHaveLength(4);
    results.forEach(r => {
      expect(r.drugCount).toBeGreaterThan(0);
      expect(Array.isArray(r.pillColors)).toBe(true);
    });
  });
});

const at = (h: number, m = 0) => new Date(2026, 5, 12, h, m);

const slot = (time: string, label: string, state: string): HeadlineSlot =>
  ({ time, label, state });

describe('buildDoseHeadline', () => {
  it('슬롯 0개 → 드실 약이 없어요', () => {
    expect(buildDoseHeadline([], at(9))).toBe('오늘은 드실 약이 없어요');
  });

  it('첫 슬롯 시간 전 → 시작 카피', () => {
    const slots = [slot('08:00', '아침', 'wait'), slot('19:00', '저녁', 'wait')];
    expect(buildDoseHeadline(slots, at(7))).toBe('8시 아침약으로 시작해요');
  });

  it('진행 중 → 다음 미복용 슬롯 안내', () => {
    const slots = [slot('08:00', '아침', 'done'), slot('19:00', '저녁', 'wait')];
    expect(buildDoseHeadline(slots, at(10))).toBe('다음은 19시 저녁약이에요');
  });

  it('분 있는 슬롯 → H시 M분', () => {
    const slots = [slot('08:00', '아침', 'done'), slot('12:30', '점심', 'wait')];
    expect(buildDoseHeadline(slots, at(10))).toBe('다음은 12시 30분 점심약이에요');
  });

  it('놓침 → 기록 없음 카피 2줄 (복용 권유 금지)', () => {
    const slots = [slot('08:00', '아침', 'done'), slot('12:30', '점심', 'wait'), slot('19:00', '저녁', 'wait')];
    expect(buildDoseHeadline(slots, at(14))).toBe('점심약 기록이 없어요\n드셨다면 체크해 주세요');
  });

  it('놓침 label 동적 — 저녁 슬롯', () => {
    const slots = [slot('19:00', '저녁', 'wait')];
    expect(buildDoseHeadline(slots, at(21))).toBe('저녁약 기록이 없어요\n드셨다면 체크해 주세요');
  });

  it('모두 완료 → 복약 끝 (스트릭은 줄3 전담 — 병합 없음)', () => {
    const slots = [slot('08:00', '아침', 'done'), slot('19:00', '저녁', 'done')];
    expect(buildDoseHeadline(slots, at(20))).toBe('오늘 복약 끝!');
  });
});

describe('deriveOverlayState', () => {
  const done = (lockedAt = 100): DoseOverrideState[number] => ({ state: 'done', lockedAt });
  const wait = (): DoseOverrideState[number] => ({ state: 'wait' });

  it('ids 빈 배열 → apiState 그대로 반환', () => {
    expect(deriveOverlayState([], 'wait', {})).toBe('wait');
    expect(deriveOverlayState([], 'now', {})).toBe('now');
    expect(deriveOverlayState([], 'done', {})).toBe('done');
  });

  it('overlay 없음 → apiState 그대로', () => {
    expect(deriveOverlayState([1, 2], 'now', {})).toBe('now');
  });

  it('모든 id done in overlay → done', () => {
    const map: DoseOverrideState = { 1: done(), 2: done() };
    expect(deriveOverlayState([1, 2], 'wait', map)).toBe('done');
  });

  it('단일 id done in overlay → done', () => {
    const map: DoseOverrideState = { 5: done() };
    expect(deriveOverlayState([5], 'wait', map)).toBe('done');
  });

  it('일부 id done, 일부 wait, apiState=wait → wait', () => {
    const map: DoseOverrideState = { 1: done(), 2: wait() };
    expect(deriveOverlayState([1, 2], 'wait', map)).toBe('wait');
  });

  it('일부 id done, 일부 wait, apiState=now → now (시간 기반 상태 보존)', () => {
    const map: DoseOverrideState = { 1: done(), 2: wait() };
    expect(deriveOverlayState([1, 2], 'now', map)).toBe('now');
  });

  it('apiState=done + 일부 wait 오버라이드 → wait (취소 실패 반영)', () => {
    const map: DoseOverrideState = { 1: done(), 2: wait() };
    expect(deriveOverlayState([1, 2], 'done', map)).toBe('wait');
  });

  it('일부 id만 overlay 있음 (partial optimistic) → apiState 유지', () => {
    const map: DoseOverrideState = { 1: done() };
    expect(deriveOverlayState([1, 2], 'wait', map)).toBe('wait');
  });
});

describe('deriveSlotStatuses', () => {
  it('done / missed / upcoming 판정 — 먹음·시간지남·아직', () => {
    const slots = [
      slot('08:00', '아침', 'done'),
      slot('12:30', '점심', 'wait'),
      slot('19:00', '저녁', 'wait'),
      slot('22:00', '취침 전', 'wait'),
    ];
    expect(deriveSlotStatuses(slots, at(14))).toEqual(['done', 'missed', 'upcoming', 'upcoming']);
  });

  it('시작 전 → 모든 미복용 슬롯이 upcoming', () => {
    const slots = [slot('08:00', '아침', 'wait'), slot('19:00', '저녁', 'wait')];
    expect(deriveSlotStatuses(slots, at(7))).toEqual(['upcoming', 'upcoming']);
  });

  it('도트 개수 = 슬롯 수 유지', () => {
    const slots = [
      slot('08:00', '아침', 'done'),
      slot('12:30', '점심', 'done'),
      slot('19:00', '저녁', 'wait'),
    ];
    expect(deriveSlotStatuses(slots, at(20))).toHaveLength(slots.length);
  });
});
