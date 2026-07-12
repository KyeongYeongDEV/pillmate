export type DateRangeFilter = 'today' | 'week' | 'month' | 'custom';

const DAY_MS = 86_400_000;
const WEEK_DAYS = 7;
const MONTH_DAYS = 30;

// 범위 시작 시각(ms). custom 은 picker 미구현이라 null(필터 없음).
export function rangeStartMs(range: DateRangeFilter, nowMs: number): number | null {
  if (range === 'today') {
    const d = new Date(nowMs);
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  }
  if (range === 'week') return nowMs - WEEK_DAYS * DAY_MS;
  if (range === 'month') return nowMs - MONTH_DAYS * DAY_MS;
  return null;
}

export function filterByDateRange<T extends { occurredAt: string }>(
  items: T[],
  range: DateRangeFilter,
  nowMs: number = Date.now(),
): T[] {
  const start = rangeStartMs(range, nowMs);
  if (start == null) return items;
  return items.filter(it => new Date(it.occurredAt).getTime() >= start);
}
