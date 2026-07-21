import { renderHook, act } from '@testing-library/react-native';
import { AppState } from 'react-native';
import { msUntilNextKstMidnight, useKstToday } from '../../src/hooks/useKstToday';
import * as calendarUtils from '../../src/utils/calendarUtils';

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const MINUTE_MS = 60 * 1000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

// KST wall-clock -> epoch ms (UTC). KST = UTC+9.
function kstEpoch(y: number, mo: number, d: number, h: number, mi: number): number {
  return Date.UTC(y, mo - 1, d, h, mi) - KST_OFFSET_MS;
}

describe('msUntilNextKstMidnight', () => {
  it('KST 23:59 → 약 1분 남음', () => {
    const now = new Date(kstEpoch(2026, 7, 14, 23, 59));
    expect(msUntilNextKstMidnight(now)).toBe(1 * MINUTE_MS);
  });

  it('KST 00:01 → 약 24시간(23시간59분) 근처', () => {
    const now = new Date(kstEpoch(2026, 7, 14, 0, 1));
    expect(msUntilNextKstMidnight(now)).toBe(DAY_MS - 1 * MINUTE_MS);
  });

  it('KST 정각 자정 → 0이 아닌 온전한 하루(24h) 반환 (0ms 루프 방지)', () => {
    const now = new Date(kstEpoch(2026, 7, 14, 0, 0));
    expect(msUntilNextKstMidnight(now)).toBe(DAY_MS);
  });

  it('기기 로컬 TZ와 무관하게 epoch 기준으로 동일 (순수 함수)', () => {
    // 동일 순간(epoch)에 대해 항상 같은 값. Date 객체의 로컬 getter 를 쓰지 않음을 방증.
    const instant = kstEpoch(2026, 12, 31, 18, 30); // KST 18:30 → 5시간30분 남음
    expect(msUntilNextKstMidnight(new Date(instant))).toBe(5 * HOUR_MS + 30 * MINUTE_MS);
  });
});

describe('useKstToday', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllTimers();
    jest.restoreAllMocks();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('마운트 시 현재 KST 날짜를 반환', () => {
    jest.spyOn(calendarUtils, 'getKstToday').mockReturnValue('2026-07-14');
    const { result } = renderHook(() => useKstToday());
    expect(result.current).toBe('2026-07-14');
  });

  it('KST 자정 경과 시 타이머 발화로 today 를 다음 날로 갱신', () => {
    const spy = jest
      .spyOn(calendarUtils, 'getKstToday')
      .mockReturnValueOnce('2026-07-14') // 초기 마운트
      .mockReturnValue('2026-07-15'); // 타이머 발화 후

    // Date.now 를 KST 23:59 로 고정 → 다음 자정까지 1분
    const base = kstEpoch(2026, 7, 14, 23, 59);
    jest.setSystemTime(base);

    const { result } = renderHook(() => useKstToday());
    expect(result.current).toBe('2026-07-14');

    act(() => {
      jest.setSystemTime(base + 1 * MINUTE_MS);
      jest.advanceTimersByTime(1 * MINUTE_MS);
    });

    expect(result.current).toBe('2026-07-15');
    expect(spy).toHaveBeenCalled();
  });

  it('자정 발화 후 다음 자정으로 재예약된다 (연속 롤오버)', () => {
    jest
      .spyOn(calendarUtils, 'getKstToday')
      .mockReturnValueOnce('2026-07-14')
      .mockReturnValueOnce('2026-07-15')
      .mockReturnValue('2026-07-16');

    const base = kstEpoch(2026, 7, 14, 23, 59);
    jest.setSystemTime(base);
    const { result } = renderHook(() => useKstToday());

    act(() => {
      jest.setSystemTime(base + 1 * MINUTE_MS);
      jest.advanceTimersByTime(1 * MINUTE_MS);
    });
    expect(result.current).toBe('2026-07-15');

    // 다음 자정(24시간 뒤)까지 진행
    act(() => {
      jest.setSystemTime(base + 1 * MINUTE_MS + DAY_MS);
      jest.advanceTimersByTime(DAY_MS);
    });
    expect(result.current).toBe('2026-07-16');
  });

  it("AppState 'active' 복귀 시 날짜가 바뀌었으면 갱신 (백그라운드 자정 케이스)", () => {
    let handler: ((s: string) => void) | undefined;
    jest.spyOn(AppState, 'addEventListener').mockImplementation((_event, cb) => {
      handler = cb as (s: string) => void;
      return { remove: jest.fn() } as any;
    });
    jest
      .spyOn(calendarUtils, 'getKstToday')
      .mockReturnValueOnce('2026-07-14') // 마운트
      .mockReturnValue('2026-07-15'); // 복귀 시 재계산

    const { result } = renderHook(() => useKstToday());
    expect(result.current).toBe('2026-07-14');

    act(() => {
      handler?.('active');
    });
    expect(result.current).toBe('2026-07-15');
  });

  it('언마운트 시 타이머 clear + AppState 구독 해제 (누수 방지)', () => {
    const remove = jest.fn();
    jest
      .spyOn(AppState, 'addEventListener')
      .mockReturnValue({ remove } as any);
    jest.spyOn(calendarUtils, 'getKstToday').mockReturnValue('2026-07-14');
    const clearSpy = jest.spyOn(global, 'clearTimeout');

    const { unmount } = renderHook(() => useKstToday());
    unmount();

    expect(remove).toHaveBeenCalledTimes(1);
    expect(clearSpy).toHaveBeenCalled();
  });
});
