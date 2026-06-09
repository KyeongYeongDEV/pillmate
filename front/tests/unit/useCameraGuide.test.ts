import { renderHook, act } from '@testing-library/react-native';
import { useCameraGuide } from '../../src/hooks/useCameraGuide';

jest.useFakeTimers();

describe('useCameraGuide', () => {
  beforeEach(() => {
    jest.clearAllTimers();
  });

  it('초기 상태: stability=loading, brightness=ok, tilt=ok', () => {
    const { result } = renderHook(() => useCameraGuide());
    expect(result.current.hints.stability).toBe('loading');
    expect(result.current.hints.brightness).toBe('ok');
    expect(result.current.hints.tilt).toBe('ok');
  });

  it('2.5초 후 stability → ok (카메라 안정화)', () => {
    const { result } = renderHook(() => useCameraGuide());
    act(() => { jest.advanceTimersByTime(2500); });
    expect(result.current.hints.stability).toBe('ok');
  });

  it('2.5초 전 allOk = false', () => {
    const { result } = renderHook(() => useCameraGuide());
    act(() => { jest.advanceTimersByTime(1000); });
    expect(result.current.allOk).toBe(false);
  });

  it('2.5초 후 allOk = true (모든 hint ok)', () => {
    const { result } = renderHook(() => useCameraGuide());
    act(() => { jest.advanceTimersByTime(2500); });
    expect(result.current.allOk).toBe(true);
  });

  it('reset() 호출 시 stability → loading 으로 초기화', () => {
    const { result } = renderHook(() => useCameraGuide());
    act(() => { jest.advanceTimersByTime(2500); });
    expect(result.current.hints.stability).toBe('ok');
    act(() => { result.current.reset(); });
    expect(result.current.hints.stability).toBe('loading');
  });

  it('reset() 후 2.5초 후 다시 ok', () => {
    const { result } = renderHook(() => useCameraGuide());
    act(() => { result.current.reset(); });
    act(() => { jest.advanceTimersByTime(2500); });
    expect(result.current.hints.stability).toBe('ok');
  });

  it('warnShake() → stability=warn', () => {
    const { result } = renderHook(() => useCameraGuide());
    act(() => { jest.advanceTimersByTime(2500); });
    act(() => { result.current.warnShake(); });
    expect(result.current.hints.stability).toBe('warn');
  });

  it('warnShake() 후 2초 지나면 다시 ok', () => {
    const { result } = renderHook(() => useCameraGuide());
    act(() => { jest.advanceTimersByTime(2500); });
    act(() => { result.current.warnShake(); });
    act(() => { jest.advanceTimersByTime(2000); });
    expect(result.current.hints.stability).toBe('ok');
  });
});
