import { renderHook, act } from '@testing-library/react-native';
import {
  useInsightPolling,
  INSIGHT_POLL_INTERVAL_MS,
  INSIGHT_POLL_MAX_ATTEMPTS,
} from '../../src/hooks/useInsightPolling';

jest.useFakeTimers();

describe('useInsightPolling', () => {
  beforeEach(() => jest.clearAllTimers());

  it('데이터 로드 전에는 polling/대기 안 함', () => {
    const refetch = jest.fn();
    const { result } = renderHook(() =>
      useInsightPolling({ hasData: false, hasInsights: false, refetch }),
    );
    act(() => { jest.advanceTimersByTime(INSIGHT_POLL_INTERVAL_MS * 3); });
    expect(result.current).toBe(false);
    expect(refetch).not.toHaveBeenCalled();
  });

  it('insight 없으면 대기 상태 + 3초 간격 refetch', () => {
    const refetch = jest.fn();
    const { result } = renderHook(() =>
      useInsightPolling({ hasData: true, hasInsights: false, refetch }),
    );
    expect(result.current).toBe(true);
    act(() => { jest.advanceTimersByTime(INSIGHT_POLL_INTERVAL_MS); });
    expect(refetch).toHaveBeenCalledTimes(1);
    act(() => { jest.advanceTimersByTime(INSIGHT_POLL_INTERVAL_MS); });
    expect(refetch).toHaveBeenCalledTimes(2);
  });

  it('MAX 회 도달 시 refetch 중단 + 대기 해제', () => {
    const refetch = jest.fn();
    const { result } = renderHook(() =>
      useInsightPolling({ hasData: true, hasInsights: false, refetch }),
    );
    // MAX 회 refetch + 그 다음 tick 에서 stop
    act(() => { jest.advanceTimersByTime(INSIGHT_POLL_INTERVAL_MS * (INSIGHT_POLL_MAX_ATTEMPTS + 1)); });
    expect(refetch).toHaveBeenCalledTimes(INSIGHT_POLL_MAX_ATTEMPTS);
    expect(result.current).toBe(false);
    // 이후 추가 tick 에도 더 호출 안 됨
    act(() => { jest.advanceTimersByTime(INSIGHT_POLL_INTERVAL_MS * 3); });
    expect(refetch).toHaveBeenCalledTimes(INSIGHT_POLL_MAX_ATTEMPTS);
  });

  it('insight 도착(hasInsights=true) 시 대기 해제 + refetch 중단', () => {
    const refetch = jest.fn();
    const { result, rerender } = renderHook(
      ({ hasInsights }: { hasInsights: boolean }) =>
        useInsightPolling({ hasData: true, hasInsights, refetch }),
      { initialProps: { hasInsights: false } },
    );
    act(() => { jest.advanceTimersByTime(INSIGHT_POLL_INTERVAL_MS); });
    expect(refetch).toHaveBeenCalledTimes(1);

    rerender({ hasInsights: true });
    expect(result.current).toBe(false);

    act(() => { jest.advanceTimersByTime(INSIGHT_POLL_INTERVAL_MS * 3); });
    expect(refetch).toHaveBeenCalledTimes(1);
  });
});
