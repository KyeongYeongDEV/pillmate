import { medSlotToTimeSlot } from '@/lib/scheduleUtils';
import { MOCK_SCHEDULE } from '@/store/slices/scheduleApi';

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
