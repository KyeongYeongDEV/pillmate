import { deriveTimeOfDay } from '../../src/lib/prescription/timeOfDay';

describe('deriveTimeOfDay — 시각에서 timeOfDay bucket 파생', () => {
  it('10:59 → MORNING (아침 경계 직전)', () => {
    expect(deriveTimeOfDay('10:59:00')).toBe('MORNING');
  });

  it('11:00 → NOON (점심 시작 경계)', () => {
    expect(deriveTimeOfDay('11:00:00')).toBe('NOON');
  });

  it('16:59 → NOON (저녁 경계 직전)', () => {
    expect(deriveTimeOfDay('16:59:00')).toBe('NOON');
  });

  it('17:00 → EVENING (저녁 시작 경계)', () => {
    expect(deriveTimeOfDay('17:00:00')).toBe('EVENING');
  });

  it('23:00 → EVENING', () => {
    expect(deriveTimeOfDay('23:00:00')).toBe('EVENING');
  });

  it('00:00 → MORNING (자정)', () => {
    expect(deriveTimeOfDay('00:00:00')).toBe('MORNING');
  });

  it('HH:mm 포맷도 처리 (초 생략)', () => {
    expect(deriveTimeOfDay('08:00')).toBe('MORNING');
  });
});
