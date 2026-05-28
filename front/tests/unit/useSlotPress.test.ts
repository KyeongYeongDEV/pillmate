// RED: useSlotPress hook 단위 테스트 (이전엔 테스트 0개)
import { renderHook, act } from '@testing-library/react-native';
import { Alert } from 'react-native';

jest.mock('@/store/hooks', () => ({
  useAppSelector: jest.fn(),
  useAppDispatch: jest.fn(() => jest.fn()),
}));

jest.mock('@/store/slices/doseLogApi', () => ({
  useCheckDoseMutation: jest.fn(),
}));

import { useSlotPress } from '@/hooks/useSlotPress';
import { LOCK_DURATION_MS } from '@/store/slices/doseStateSlice';
import { useAppSelector } from '@/store/hooks';
import { useCheckDoseMutation } from '@/store/slices/doseLogApi';

const mockSelector = useAppSelector as jest.Mock;
const mockMutation = useCheckDoseMutation as jest.Mock;
const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});

const DOSE_LOG_ID = 5;

function makeMap(lockedAt?: number) {
  return lockedAt != null ? { [DOSE_LOG_ID]: { state: 'done' as const, lockedAt } } : {};
}

describe('useSlotPress', () => {
  let checkDose: jest.Mock;

  beforeEach(() => {
    checkDose = jest.fn();
    mockMutation.mockReturnValue([checkDose, {}]);
    alertSpy.mockClear();
    checkDose.mockClear();
  });

  it('doseLogId undefined → 조기 반환, mutation 미호출', () => {
    mockSelector.mockReturnValue(makeMap());
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(undefined, 'wait'); });
    expect(checkDose).not.toHaveBeenCalled();
  });

  it('state=wait → TAKE action으로 mutation 호출', () => {
    mockSelector.mockReturnValue(makeMap());
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'wait'); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: DOSE_LOG_ID, action: 'TAKE' });
    expect(alertSpy).not.toHaveBeenCalled();
  });

  it('state=now → TAKE action으로 mutation 호출', () => {
    mockSelector.mockReturnValue(makeMap());
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'now'); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: DOSE_LOG_ID, action: 'TAKE' });
  });

  it('state=done + 잠금 해제 상태 → SKIP action으로 mutation 호출', () => {
    // lockedAt = 지금으로부터 1초 전 → 아직 grace period 이내
    const lockedAt = Date.now() - 1_000;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'done'); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: DOSE_LOG_ID, action: 'SKIP' });
    expect(alertSpy).not.toHaveBeenCalled();
  });

  it('state=done + 잠금 완료 (60초 초과) → Alert 표시, mutation 미호출', () => {
    // lockedAt = 60초 이상 전 → 잠금 완료
    const lockedAt = Date.now() - LOCK_DURATION_MS - 1_000;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'done'); });
    expect(alertSpy).toHaveBeenCalledWith('취소 불가', '복약 완료는 60초 후 취소할 수 없습니다.');
    expect(checkDose).not.toHaveBeenCalled();
  });

  it('state=done + doseLogId가 map에 없음 → SKIP 호출 (락 없음)', () => {
    mockSelector.mockReturnValue({});
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'done'); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: DOSE_LOG_ID, action: 'SKIP' });
  });

  it('state=done + lockedAt 정확히 60초 전 → 경계값 잠금 완료', () => {
    // LOCK_DURATION_MS = 60_000ms: now - lockedAt >= 60000 → locked
    const lockedAt = Date.now() - LOCK_DURATION_MS;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'done'); });
    expect(alertSpy).toHaveBeenCalledWith('취소 불가', '복약 완료는 60초 후 취소할 수 없습니다.');
    expect(checkDose).not.toHaveBeenCalled();
  });
});
