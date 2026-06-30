import { renderHook, act } from '@testing-library/react-native';
import { Alert, type AlertButton } from 'react-native';

jest.mock('@/store/hooks', () => ({
  useAppDispatch: jest.fn(() => jest.fn()),
}));

jest.mock('@/store/slices/doseLogApi', () => ({
  useCheckDoseMutation: jest.fn(),
  useBulkCheckDoseMutation: jest.fn(),
}));

import { useSlotPress } from '@/hooks/useSlotPress';
import { useCheckDoseMutation, useBulkCheckDoseMutation } from '@/store/slices/doseLogApi';

const mockCheck = useCheckDoseMutation as jest.Mock;
const mockBulk = useBulkCheckDoseMutation as jest.Mock;
const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});

function findAlertButton(label: string): AlertButton | undefined {
  const buttons = alertSpy.mock.calls[0][2] as AlertButton[];
  return buttons.find(b => b.text === label);
}

describe('useSlotPress', () => {
  let checkDose: jest.Mock;
  let bulkCheckDose: jest.Mock;

  beforeEach(() => {
    checkDose = jest.fn();
    bulkCheckDose = jest.fn();
    mockCheck.mockReturnValue([checkDose, {}]);
    mockBulk.mockReturnValue([bulkCheckDose, {}]);
    alertSpy.mockClear();
  });

  it('빈 배열 → 조기 반환, mutation 미호출', () => {
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([], 'wait'); });
    expect(checkDose).not.toHaveBeenCalled();
    expect(bulkCheckDose).not.toHaveBeenCalled();
  });

  it('pressDone_singleId_callsCheckDose — 단일 id wait → checkDose TAKE', () => {
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([5], 'wait'); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: 5, action: 'TAKE' });
    expect(bulkCheckDose).not.toHaveBeenCalled();
  });

  it('pressDone_multipleIds_callsBulkCheckOnce — 복수 id wait → bulkCheck 1회 TAKE', () => {
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([5, 6, 7], 'wait'); });
    expect(bulkCheckDose).toHaveBeenCalledTimes(1);
    expect(bulkCheckDose).toHaveBeenCalledWith({ doseLogIds: [5, 6, 7], action: 'TAKE' });
    expect(checkDose).not.toHaveBeenCalled();
  });

  it('state=now 단일 → checkDose TAKE', () => {
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([5], 'now'); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: 5, action: 'TAKE' });
  });

  it('state=done 단일 → confirm Alert, "예" → checkDose CANCEL', () => {
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([5], 'done'); });
    expect(alertSpy).toHaveBeenCalledWith('복약 취소', '취소하시겠습니까?', expect.any(Array));
    expect(checkDose).not.toHaveBeenCalled();
    act(() => { findAlertButton('예')!.onPress?.(); });
    expect(checkDose).toHaveBeenCalledWith({ doseLogId: 5, action: 'CANCEL' });
    expect(bulkCheckDose).not.toHaveBeenCalled();
  });

  it('state=done 복수 → confirm Alert, "예" → bulkCheck 1회 CANCEL', () => {
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([5, 6, 7], 'done'); });
    expect(checkDose).not.toHaveBeenCalled();
    expect(bulkCheckDose).not.toHaveBeenCalled();
    act(() => { findAlertButton('예')!.onPress?.(); });
    expect(bulkCheckDose).toHaveBeenCalledTimes(1);
    expect(bulkCheckDose).toHaveBeenCalledWith({ doseLogIds: [5, 6, 7], action: 'CANCEL' });
  });

  it('state=done "아니요" → no-op (mutation 미호출)', () => {
    const { result } = renderHook(() => useSlotPress());
    act(() => { result.current([5], 'done'); });
    act(() => { findAlertButton('아니요')!.onPress?.(); });
    expect(checkDose).not.toHaveBeenCalled();
    expect(bulkCheckDose).not.toHaveBeenCalled();
  });
});
