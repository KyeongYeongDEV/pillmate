import { MOCK_SCHEDULE } from '@/store/slices/scheduleApi';

describe('MOCK_SCHEDULE — 단일 진실 소스', () => {
  it('slots 4개', () => {
    expect(MOCK_SCHEDULE.slots).toHaveLength(4);
  });

  it('slots[0] (morning) drugCount 정의', () => {
    expect(MOCK_SCHEDULE.slots[0].drugCount).toBeDefined();
  });

  it('slots[0] (morning) drugCount === 2', () => {
    expect(MOCK_SCHEDULE.slots[0].drugCount).toBe(2);
  });

  it('slots[0] (morning) pillColors 정의', () => {
    expect(MOCK_SCHEDULE.slots[0].pillColors).toBeDefined();
  });

  it('모든 슬롯 drugCount 정의', () => {
    MOCK_SCHEDULE.slots.forEach(s => expect(s.drugCount).toBeDefined());
  });

  it('모든 슬롯 pillColors 정의', () => {
    MOCK_SCHEDULE.slots.forEach(s => expect(s.pillColors).toBeDefined());
  });

  it('slots[0] items 보존 (schedule.tsx 계속 사용)', () => {
    expect(MOCK_SCHEDULE.slots[0].items).toHaveLength(2);
  });
});
