import { renderHook, act } from '@testing-library/react-native';
import { Alert, type AlertButton } from 'react-native';

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

function findAlertButton(label: string): AlertButton | undefined {
  const buttons = alertSpy.mock.calls[0][2] as AlertButton[];
  return buttons.find(b => b.text === label);
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

  it('state=done + 60초 이내 → CANCEL action 즉시 발사 (자유 토글, SKIP 아님)', () => {
    const lockedAt = Date.now() - 1_000;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'done'); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: DOSE_LOG_ID, action: 'CANCEL' });
    expect(alertSpy).not.toHaveBeenCalled();
  });

  it('state=done + 60초 초과 → confirm Alert 표시, 즉시 발사 없음', () => {
    const lockedAt = Date.now() - LOCK_DURATION_MS - 1_000;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'done'); });
    expect(alertSpy).toHaveBeenCalledWith(
      '복약 취소',
      '취소하시겠습니까?',
      expect.any(Array),
    );
    expect(checkDose).not.toHaveBeenCalled();
  });

  it('confirm에서 "예" 탭 → CANCEL action 발사', () => {
    const lockedAt = Date.now() - LOCK_DURATION_MS - 1_000;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'done'); });

    const yesBtn = findAlertButton('예');
    expect(yesBtn).toBeDefined();
    act(() => { yesBtn!.onPress?.(); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: DOSE_LOG_ID, action: 'CANCEL' });
  });

  it('confirm에서 "아니요" 탭 → no-op (mutation 미호출)', () => {
    const lockedAt = Date.now() - LOCK_DURATION_MS - 1_000;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'done'); });

    const noBtn = findAlertButton('아니요');
    expect(noBtn).toBeDefined();
    act(() => { noBtn!.onPress?.(); });
    expect(checkDose).not.toHaveBeenCalled();
  });

  it('state=done + doseLogId가 map에 없음 → CANCEL 즉시 발사 (락 없음)', () => {
    mockSelector.mockReturnValue({});
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'done'); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: DOSE_LOG_ID, action: 'CANCEL' });
  });

  it('state=done + lockedAt 정확히 60초 전 → 경계값 confirm 분기', () => {
    const lockedAt = Date.now() - LOCK_DURATION_MS;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(DOSE_LOG_ID, 'done'); });
    expect(alertSpy).toHaveBeenCalledWith('복약 취소', '취소하시겠습니까?', expect.any(Array));
    expect(checkDose).not.toHaveBeenCalled();
  });
});
