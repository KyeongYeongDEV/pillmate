import { MOCK_SCHEDULE, toAdherenceMap } from '@/store/slices/scheduleApi';

describe('toAdherenceMap — /schedules/month 응답 변환', () => {
  it('FULL/PARTIAL/MISS → 소문자 AdherenceLevel 맵', () => {
    const map = toAdherenceMap({
      month: '2026-06',
      days: [
        { date: '2026-06-10', totalCount: 4, takenCount: 4, adherence: 'FULL' },
        { date: '2026-06-11', totalCount: 4, takenCount: 1, adherence: 'PARTIAL' },
        { date: '2026-06-09', totalCount: 4, takenCount: 0, adherence: 'MISS' },
      ],
    });
    expect(map).toEqual({
      '2026-06-10': 'full',
      '2026-06-11': 'partial',
      '2026-06-09': 'miss',
    });
  });

  it('UPCOMING(미래 날짜 미복용) → upcoming', () => {
    const map = toAdherenceMap({
      month: '2026-06',
      days: [
        { date: '2026-06-30', totalCount: 4, takenCount: 0, adherence: 'UPCOMING' },
        { date: '2026-06-29', totalCount: 4, takenCount: 4, adherence: 'FULL' },
      ],
    });
    expect(map).toEqual({
      '2026-06-30': 'upcoming',
      '2026-06-29': 'full',
    });
  });

  it('days 빈 배열 → 빈 맵', () => {
    expect(toAdherenceMap({ month: '2026-06', days: [] })).toEqual({});
  });

  it('응답 없음(undefined) → 빈 맵', () => {
    expect(toAdherenceMap(undefined)).toEqual({});
  });
});

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
