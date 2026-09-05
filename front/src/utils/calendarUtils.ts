import type { MedSlot } from '@/types/schedule';

const WEEKDAYS_KO = ['일', '월', '화', '수', '목', '금', '토'];
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

export type AdherenceLevel = 'full' | 'partial' | 'miss' | 'upcoming';

export function deriveAdherence(slots: MedSlot[]): AdherenceLevel | null {
  if (slots.length === 0) return null;
  const doneCount = slots.filter(s => s.state === 'done').length;
  if (doneCount === slots.length) return 'full';
  if (doneCount > 0) return 'partial';
  return 'miss';
}

export function toDateString(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

export function toMonthString(year: number, month: number): string {
  return `${year}-${String(month).padStart(2, '0')}`;
}

export function toKstDateString(at: Date): string {
  return new Date(at.getTime() + KST_OFFSET_MS).toISOString().slice(0, 10);
}

export function getKstToday(): string {
  return toKstDateString(new Date());
}

export function isEditableDate(selectedDate: string, now: Date): boolean {
  return selectedDate === toKstDateString(now);
}

export function prevDate(dateStr: string): string {
  const [y, m, d] = dateStr.split('-').map(Number);
  const shifted = new Date(y, m - 1, d - 1);
  return toDateString(shifted.getFullYear(), shifted.getMonth() + 1, shifted.getDate());
}

export function deriveStreak(
  adherenceByDate: Record<string, AdherenceLevel>,
  today: string,
  todayComplete: boolean,
): number {
  const earliestRecorded = findEarliestDate(adherenceByDate);
  let streak = todayComplete ? 1 : 0;
  let cursor = prevDate(today);
  while (earliestRecorded && cursor >= earliestRecorded) {
    const level = adherenceByDate[cursor];
    if (level && level !== 'full') break;
    if (level === 'full') streak += 1;
    cursor = prevDate(cursor);
  }
  return streak;
}

export function isStreakUnbrokenThrough(
  adherenceByDate: Record<string, AdherenceLevel>,
  today: string,
  floorDate: string,
): boolean {
  let cursor = prevDate(today);
  while (cursor >= floorDate) {
    const level = adherenceByDate[cursor];
    if (level && level !== 'full') return false;
    cursor = prevDate(cursor);
  }
  return true;
}

function findEarliestDate(adherenceByDate: Record<string, AdherenceLevel>): string | null {
  const dates = Object.keys(adherenceByDate);
  return dates.length > 0 ? dates.reduce((a, b) => (a < b ? a : b)) : null;
}

export function prevMonth(year: number, month: number): { year: number; month: number } {
  return month === 1 ? { year: year - 1, month: 12 } : { year, month: month - 1 };
}

export function nextMonth(year: number, month: number): { year: number; month: number } {
  return month === 12 ? { year: year + 1, month: 1 } : { year, month: month + 1 };
}

export function buildCalendarRows(year: number, month: number): (string | null)[][] {
  const firstDow = new Date(year, month - 1, 1).getDay();
  const lastDay = new Date(year, month, 0).getDate();
  const totalCells = Math.ceil((firstDow + lastDay) / 7) * 7;

  const cells: (string | null)[] = Array.from({ length: totalCells }, (_, i) => {
    const day = i - firstDow + 1;
    return day >= 1 && day <= lastDay ? toDateString(year, month, day) : null;
  });

  const rows: (string | null)[][] = [];
  for (let i = 0; i < cells.length; i += 7) {
    rows.push(cells.slice(i, i + 7));
  }
  return rows;
}

export function formatMonthDay(dateStr: string): string {
  const [y, m, d] = dateStr.split('-').map(Number);
  const dow = new Date(y, m - 1, d).getDay();
  return `${m}월 ${d}일 ${WEEKDAYS_KO[dow]}`;
}

export function formatFullDate(dateStr: string): string {
  return `${formatMonthDay(dateStr)}요일`;
}

export function formatDayLabel(dateStr: string, todayStr: string): string {
  const suffix = formatMonthDay(dateStr);
  return dateStr === todayStr ? `오늘 · ${suffix}` : suffix;
}
