import {
  buildCalendarRows,
  prevMonth,
  nextMonth,
  toDateString,
  toMonthString,
  formatDayLabel,
  formatMonthDay,
  formatFullDate,
  deriveAdherence,
} from '../../src/utils/calendarUtils';
import type { MedSlot } from '../../src/types/schedule';

describe('toDateString', () => {
  it('2026-06-01 포맷', () => {
    expect(toDateString(2026, 6, 1)).toBe('2026-06-01');
  });
  it('월/일 두 자리 패딩', () => {
    expect(toDateString(2026, 1, 5)).toBe('2026-01-05');
  });
});

describe('toMonthString', () => {
  it('2026-06 포맷', () => {
    expect(toMonthString(2026, 6)).toBe('2026-06');
  });
  it('두 자리 월은 그대로', () => {
    expect(toMonthString(2026, 12)).toBe('2026-12');
  });
});

describe('prevMonth / nextMonth', () => {
  it('일반 이전 달', () => {
    expect(prevMonth(2026, 6)).toEqual({ year: 2026, month: 5 });
  });
  it('1월 → 이전 해 12월', () => {
    expect(prevMonth(2026, 1)).toEqual({ year: 2025, month: 12 });
  });
  it('일반 다음 달', () => {
    expect(nextMonth(2026, 6)).toEqual({ year: 2026, month: 7 });
  });
  it('12월 → 다음 해 1월', () => {
    expect(nextMonth(2026, 12)).toEqual({ year: 2027, month: 1 });
  });
});

describe('buildCalendarRows', () => {
  it('2026-06: 첫 셀 null (월요일 시작), 1일 = 두번째 셀', () => {
    const rows = buildCalendarRows(2026, 6);
    expect(rows[0][0]).toBeNull();
    expect(rows[0][1]).toBe('2026-06-01');
  });

  it('2026-06: 마지막 날짜가 30일', () => {
    const rows = buildCalendarRows(2026, 6);
    const allDates = rows.flat().filter(Boolean) as string[];
    expect(allDates[allDates.length - 1]).toBe('2026-06-30');
  });

  it('2026-06: 5행 × 7열', () => {
    const rows = buildCalendarRows(2026, 6);
    expect(rows.length).toBe(5);
    expect(rows[0].length).toBe(7);
  });

  it('2024-02: 29일 (윤년)', () => {
    const rows = buildCalendarRows(2024, 2);
    const allDates = rows.flat().filter(Boolean) as string[];
    expect(allDates[allDates.length - 1]).toBe('2024-02-29');
  });

  it('2025-11: 1일 = 토요일 (column 6)', () => {
    const rows = buildCalendarRows(2025, 11);
    // Nov 1 2025 is Saturday → first 6 cells null
    expect(rows[0][6]).toBe('2025-11-01');
    for (let i = 0; i < 6; i++) expect(rows[0][i]).toBeNull();
  });

  it('2025-11: 30일 = 마지막 날짜', () => {
    const rows = buildCalendarRows(2025, 11);
    const allDates = rows.flat().filter(Boolean) as string[];
    expect(allDates[allDates.length - 1]).toBe('2025-11-30');
  });

  it('각 행은 7셀', () => {
    const rows = buildCalendarRows(2026, 3);
    rows.forEach(row => expect(row.length).toBe(7));
  });
});

describe('deriveAdherence', () => {
  const slot = (id: string, state: MedSlot['state']): MedSlot =>
    ({ id, time: '08:00', label: '아침', state, items: ['암로디핀 5mg'] });

  it('전체 done → full', () => {
    expect(deriveAdherence([slot('a', 'done'), slot('b', 'done')])).toBe('full');
  });

  it('일부 done → partial', () => {
    expect(deriveAdherence([slot('a', 'done'), slot('b', 'wait')])).toBe('partial');
  });

  it('0 done → miss', () => {
    expect(deriveAdherence([slot('a', 'wait'), slot('b', 'now')])).toBe('miss');
  });

  it('빈 슬롯 → null (스케줄 없는 날)', () => {
    expect(deriveAdherence([])).toBeNull();
  });
});

describe('formatDayLabel', () => {
  it('오늘이면 "오늘 · M월 D일 요일"', () => {
    const today = '2026-06-12';
    expect(formatDayLabel('2026-06-12', today)).toBe('오늘 · 6월 12일 금');
  });

  it('다른 날이면 "M월 D일 요일" (오늘 없음)', () => {
    expect(formatDayLabel('2026-06-01', '2026-06-12')).toBe('6월 1일 월');
  });

  it('월 경계: 2026-01-01', () => {
    expect(formatDayLabel('2026-01-01', '2026-06-12')).toBe('1월 1일 목');
  });
});

describe('formatMonthDay', () => {
  it('"M월 D일 요일" 형식', () => {
    expect(formatMonthDay('2026-06-12')).toBe('6월 12일 금');
  });

  it('일요일 처리', () => {
    expect(formatMonthDay('2026-06-14')).toBe('6월 14일 일');
  });
});

describe('formatFullDate', () => {
  it('"M월 D일 X요일" 형식', () => {
    expect(formatFullDate('2026-06-12')).toBe('6월 12일 금요일');
  });

  it('일요일 → 일요일', () => {
    expect(formatFullDate('2026-06-14')).toBe('6월 14일 일요일');
  });
});
