import { filterByDateRange, rangeStartMs } from '@/lib/activityDateFilter';

const NOW = new Date('2026-06-30T12:00:00Z').getTime();
const DAY = 86_400_000;
const at = (offsetDays: number) => ({ occurredAt: new Date(NOW - offsetDays * DAY).toISOString() });

describe('filterByDateRange', () => {
  it("week: 7일 이내만 (8일 전 제외)", () => {
    const r = filterByDateRange([at(1), at(7), at(8)], 'week', NOW);
    expect(r).toHaveLength(2); // 1일·7일(경계 포함), 8일 제외
  });

  it('month: 30일 이내만 (31일 전 제외)', () => {
    const r = filterByDateRange([at(10), at(31)], 'month', NOW);
    expect(r).toHaveLength(1);
    expect(r[0]).toEqual(at(10));
  });

  it('today: 오늘 것 포함, 2일 전 제외', () => {
    const r = filterByDateRange([at(0), at(2)], 'today', NOW);
    expect(r).toHaveLength(1);
    expect(r[0]).toEqual(at(0));
  });

  it('custom: 필터 없음 (전부 반환 — picker 추후)', () => {
    const items = [at(0), at(100)];
    expect(filterByDateRange(items, 'custom', NOW)).toEqual(items);
  });

  it('rangeStartMs: week/month/today 경계, custom null', () => {
    expect(rangeStartMs('week', NOW)).toBe(NOW - 7 * DAY);
    expect(rangeStartMs('month', NOW)).toBe(NOW - 30 * DAY);
    expect(rangeStartMs('custom', NOW)).toBeNull();
    expect(rangeStartMs('today', NOW)).toBeLessThanOrEqual(NOW);
  });
});
