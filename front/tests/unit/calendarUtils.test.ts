import {
  buildCalendarRows,
  prevMonth,
  nextMonth,
  toDateString,
  toMonthString,
  formatDayLabel,
  formatMonthDay,
  formatFullDate,
  prevDate,
  deriveStreak,
  deriveAdherence,
  toKstDateString,
  getKstToday,
  isEditableDate,
  isStreakUnbrokenThrough,
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

describe('prevDate', () => {
  it('하루 전 날짜', () => {
    expect(prevDate('2026-06-12')).toBe('2026-06-11');
  });

  it('월 경계 — 6/1 전날은 5/31', () => {
    expect(prevDate('2026-06-01')).toBe('2026-05-31');
  });

  it('연 경계 — 1/1 전날은 작년 12/31', () => {
    expect(prevDate('2026-01-01')).toBe('2025-12-31');
  });
});

describe('deriveStreak', () => {
  const TODAY = '2026-06-12';

  it('어제 미달성 → 0', () => {
    expect(deriveStreak({ '2026-06-11': 'partial' }, TODAY, false)).toBe(0);
  });

  it('어제만 FULL → 1', () => {
    expect(deriveStreak({ '2026-06-11': 'full', '2026-06-10': 'miss' }, TODAY, false)).toBe(1);
  });

  it('3일 연속 FULL → 3', () => {
    const map: Record<string, 'full'> = {
      '2026-06-11': 'full', '2026-06-10': 'full', '2026-06-09': 'full',
    };
    expect(deriveStreak(map, TODAY, false)).toBe(3);
  });

  it('월 경계 넘는 연속 — 6/1 + 5/31 + 5/30', () => {
    const map: Record<string, 'full'> = {
      '2026-06-01': 'full', '2026-05-31': 'full', '2026-05-30': 'full',
    };
    expect(deriveStreak(map, '2026-06-02', false)).toBe(3);
  });

  it('오늘 완료 포함 — 어제·그제 FULL + 오늘 완료 → 3', () => {
    const map: Record<string, 'full'> = { '2026-06-11': 'full', '2026-06-10': 'full' };
    expect(deriveStreak(map, TODAY, true)).toBe(3);
  });

  it('오늘만 완료 (어제 기록 없음) → 1', () => {
    expect(deriveStreak({}, TODAY, true)).toBe(1);
  });

  it('약 없는 날(기록 없음) 끼어도 연속 유지 — 건너뜀', () => {
    const map: Record<string, 'full'> = { '2026-06-11': 'full', '2026-06-09': 'full' };
    expect(deriveStreak(map, TODAY, false)).toBe(2);
  });

  it('빈 날 건너뛰다 PARTIAL 만나면 중단', () => {
    const map: Record<string, 'full' | 'partial'> = {
      '2026-06-11': 'full', '2026-06-09': 'partial', '2026-06-08': 'full',
    };
    expect(deriveStreak(map, TODAY, false)).toBe(1);
  });
});

describe('isStreakUnbrokenThrough', () => {
  const TODAY = '2026-06-12';
  const MONTH_START = '2026-06-01';

  it('어제부터 월초까지 전부 FULL → true (전월 조회 필요)', () => {
    const map: Record<string, 'full'> = {};
    for (let d = 1; d <= 11; d++) map[`2026-06-${String(d).padStart(2, '0')}`] = 'full';
    expect(isStreakUnbrokenThrough(map, TODAY, MONTH_START)).toBe(true);
  });

  it('중간에 PARTIAL → false (전월 조회 불필요)', () => {
    const map: Record<string, 'full' | 'partial'> = {
      '2026-06-11': 'full', '2026-06-10': 'partial',
    };
    expect(isStreakUnbrokenThrough(map, TODAY, MONTH_START)).toBe(false);
  });

  it('월초 빈 날(약 없는 날) 끼어도 끊김 아님 → true — #145 ② 핵심', () => {
    const map: Record<string, 'full'> = {
      '2026-06-11': 'full', '2026-06-10': 'full', '2026-06-03': 'full',
    };
    expect(isStreakUnbrokenThrough(map, TODAY, MONTH_START)).toBe(true);
  });

  it('오늘이 1일 — 어제가 전월이라 항상 true', () => {
    expect(isStreakUnbrokenThrough({}, '2026-06-01', MONTH_START)).toBe(true);
  });
});

describe('toKstDateString', () => {
  it('UTC 15:00 = KST 다음날 자정 → 다음날 날짜', () => {
    expect(toKstDateString(new Date(Date.UTC(2026, 5, 11, 15, 0)))).toBe('2026-06-12');
  });

  it('UTC 14:59 = KST 같은 날 23:59 → 같은 날짜', () => {
    expect(toKstDateString(new Date(Date.UTC(2026, 5, 11, 14, 59)))).toBe('2026-06-11');
  });

  it('월 경계 — UTC 5/31 15:00 → KST 6/1', () => {
    expect(toKstDateString(new Date(Date.UTC(2026, 4, 31, 15, 0)))).toBe('2026-06-01');
  });
});

describe('getKstToday', () => {
  it('호출 시점 KST 날짜 — YYYY-MM-DD 형식', () => {
    expect(getKstToday()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('toKstDateString(now) 와 동일 기준', () => {
    expect(getKstToday()).toBe(toKstDateString(new Date()));
  });
});

describe('isEditableDate', () => {
  const NOON_KST = new Date(Date.UTC(2026, 5, 12, 3, 0));

  it('오늘 → true', () => {
    expect(isEditableDate('2026-06-12', NOON_KST)).toBe(true);
  });

  it('어제 → false', () => {
    expect(isEditableDate('2026-06-11', NOON_KST)).toBe(false);
  });

  it('내일 → false', () => {
    expect(isEditableDate('2026-06-13', NOON_KST)).toBe(false);
  });

  it('KST 자정 경계 — UTC 15시부터 다음날이 편집 가능', () => {
    const kstMidnight = new Date(Date.UTC(2026, 5, 11, 15, 0));
    expect(isEditableDate('2026-06-12', kstMidnight)).toBe(true);
    expect(isEditableDate('2026-06-11', kstMidnight)).toBe(false);
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
