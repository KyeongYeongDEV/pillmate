import { renderHook, act } from '@testing-library/react-native';
import { Alert, type AlertButton } from 'react-native';

let mockDispatch: jest.Mock;

jest.mock('@/store/hooks', () => ({
  useAppSelector: jest.fn(),
  useAppDispatch: jest.fn(() => mockDispatch),
}));

jest.mock('@/store/slices/doseLogApi', () => ({
  useCheckDoseMutation: jest.fn(),
}));

import { useSlotPress } from '@/hooks/useSlotPress';
import { LOCK_DURATION_MS, markDone, markWait } from '@/store/slices/doseStateSlice';
import { useAppSelector } from '@/store/hooks';
import { useCheckDoseMutation } from '@/store/slices/doseLogApi';

const mockSelector = useAppSelector as jest.Mock;
const mockMutation = useCheckDoseMutation as jest.Mock;
const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});

const DOSE_LOG_ID = 5;
const IDS = [DOSE_LOG_ID];

function makeMap(lockedAt?: number) {
  return lockedAt != null ? { [DOSE_LOG_ID]: { state: 'done' as const, lockedAt } } : {};
}

function findAlertButton(label: string): AlertButton | undefined {
  const buttons = alertSpy.mock.calls[0][2] as AlertButton[];
  return buttons.find(b => b.text === label);
}

const flushAsync = () => act(async () => { await Promise.resolve(); await Promise.resolve(); });

describe('useSlotPress', () => {
  let checkDose: jest.Mock;

  beforeEach(() => {
    mockDispatch = jest.fn();
    checkDose = jest.fn().mockReturnValue({ unwrap: jest.fn().mockResolvedValue({}) });
    mockMutation.mockReturnValue([checkDose, {}]);
    alertSpy.mockClear();
    checkDose.mockClear();
  });

  it('빈 배열 → 조기 반환, mutation 미호출', () => {
    mockSelector.mockReturnValue(makeMap());
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([], 'wait'); });
    expect(checkDose).not.toHaveBeenCalled();
  });

  it('state=wait → TAKE action으로 mutation 호출', () => {
    mockSelector.mockReturnValue(makeMap());
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(IDS, 'wait'); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: DOSE_LOG_ID, action: 'TAKE' });
    expect(alertSpy).not.toHaveBeenCalled();
  });

  it('state=now → TAKE action으로 mutation 호출', () => {
    mockSelector.mockReturnValue(makeMap());
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(IDS, 'now'); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: DOSE_LOG_ID, action: 'TAKE' });
  });

  it('복수 doseLogIds → skipOptimistic=true 로 각 id TAKE 호출', () => {
    mockSelector.mockReturnValue({});
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([5, 6, 7], 'wait'); });
    expect(checkDose).toHaveBeenCalledTimes(3);
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: 5, action: 'TAKE', skipOptimistic: true });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: 6, action: 'TAKE', skipOptimistic: true });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: 7, action: 'TAKE', skipOptimistic: true });
  });

  it('복수 id TAKE 전부 성공 → dispatch(markDone) 각 id 호출', async () => {
    mockSelector.mockReturnValue({});
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([5, 6, 7], 'wait'); });
    await flushAsync();
    expect(alertSpy).not.toHaveBeenCalled();
    expect(mockDispatch).toHaveBeenCalledWith(markDone({ doseLogId: 5 }));
    expect(mockDispatch).toHaveBeenCalledWith(markDone({ doseLogId: 6 }));
    expect(mockDispatch).toHaveBeenCalledWith(markDone({ doseLogId: 7 }));
  });

  it('복수 id TAKE 일부 실패 → Alert 표시 + 성공한 id만 markDone', async () => {
    mockSelector.mockReturnValue({});
    checkDose = jest.fn()
      .mockReturnValueOnce({ unwrap: jest.fn().mockRejectedValue(new Error('fail')) }) // id 5 실패
      .mockReturnValue({ unwrap: jest.fn().mockResolvedValue({}) }); // id 6, 7 성공
    mockMutation.mockReturnValue([checkDose, {}]);

    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([5, 6, 7], 'wait'); });
    await flushAsync();

    expect(alertSpy).toHaveBeenCalledWith('복약 기록 실패', expect.any(String));
    expect(mockDispatch).toHaveBeenCalledWith(markDone({ doseLogId: 6 }));
    expect(mockDispatch).toHaveBeenCalledWith(markDone({ doseLogId: 7 }));
    expect(mockDispatch).not.toHaveBeenCalledWith(markDone({ doseLogId: 5 }));
  });

  it('단일 id TAKE → skipOptimistic 없음, dispatch 미호출 (기존 동작 유지)', () => {
    mockSelector.mockReturnValue(makeMap());
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(IDS, 'wait'); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: DOSE_LOG_ID, action: 'TAKE' });
    expect(mockDispatch).not.toHaveBeenCalled();
  });

  it('state=done → 락 이내여도 항상 confirm Alert (lock 분기 제거)', () => {
    const lockedAt = Date.now() - 1_000;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(IDS, 'done'); });
    expect(alertSpy).toHaveBeenCalledWith('복약 취소', '취소하시겠습니까?', expect.any(Array));
    expect(checkDose).not.toHaveBeenCalled();
  });

  it('state=done + 30초 초과 → confirm Alert 표시, 즉시 발사 없음', () => {
    const lockedAt = Date.now() - LOCK_DURATION_MS - 1_000;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(IDS, 'done'); });
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
    act(() => { result.current(IDS, 'done'); });

    const yesBtn = findAlertButton('예');
    expect(yesBtn).toBeDefined();
    act(() => { yesBtn!.onPress?.(); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: DOSE_LOG_ID, action: 'CANCEL' });
  });

  it('confirm에서 "아니요" 탭 → no-op (mutation 미호출)', () => {
    const lockedAt = Date.now() - LOCK_DURATION_MS - 1_000;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(IDS, 'done'); });

    const noBtn = findAlertButton('아니요');
    expect(noBtn).toBeDefined();
    act(() => { noBtn!.onPress?.(); });
    expect(checkDose).not.toHaveBeenCalled();
  });

  it('state=done + doseLogId가 map에 없음 → 항상 confirm Alert (락 없음)', () => {
    mockSelector.mockReturnValue({});
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(IDS, 'done'); });
    expect(alertSpy).toHaveBeenCalledWith('복약 취소', '취소하시겠습니까?', expect.any(Array));
    expect(checkDose).not.toHaveBeenCalled();
  });

  it('복수 id CANCEL "예" → 각 id skipOptimistic=true 로 CANCEL 호출', () => {
    mockSelector.mockReturnValue({});
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([5, 6, 7], 'done'); });
    const yesBtn = findAlertButton('예');
    expect(yesBtn).toBeDefined();
    act(() => { yesBtn!.onPress?.(); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: 5, action: 'CANCEL', skipOptimistic: true });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: 6, action: 'CANCEL', skipOptimistic: true });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: 7, action: 'CANCEL', skipOptimistic: true });
  });

  it('state=done + lockedAt 정확히 30초 전 → 경계값 confirm 분기', () => {
    const lockedAt = Date.now() - LOCK_DURATION_MS;
    mockSelector.mockReturnValue(makeMap(lockedAt));
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current(IDS, 'done'); });
    expect(alertSpy).toHaveBeenCalledWith('복약 취소', '취소하시겠습니까?', expect.any(Array));
    expect(checkDose).not.toHaveBeenCalled();
  });
});
