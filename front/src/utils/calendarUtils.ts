const WEEKDAYS_KO = ['일', '월', '화', '수', '목', '금', '토'];

export function toDateString(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
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

export function formatDayLabel(dateStr: string, todayStr: string): string {
  const [y, m, d] = dateStr.split('-').map(Number);
  const dow = new Date(y, m - 1, d).getDay();
  const suffix = `${m}월 ${d}일 ${WEEKDAYS_KO[dow]}`;
  return dateStr === todayStr ? `오늘 · ${suffix}` : suffix;
}
