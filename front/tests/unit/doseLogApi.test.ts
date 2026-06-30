// RED: doseLogApi.ts 미존재 → 전 항목 FAIL 예상
import {
  doseLogApiSlice,
  useCheckDoseMutation,
  invalidateAfterDoseMutation,
} from '@/store/slices/doseLogApi';
import type { CheckDoseInput, DoseStatus } from '@/types/doseLog';

describe('invalidateAfterDoseMutation — cross-slice 무효화', () => {
  it('성공 후 scheduleApi(MonthSchedule) + caregroupApi(Group) 둘 다 invalidate dispatch', () => {
    const dispatch = jest.fn();
    invalidateAfterDoseMutation(dispatch);
    const types = dispatch.mock.calls.map(c => c[0]?.type);
    expect(types).toContain('scheduleApi/invalidateTags');
    expect(types).toContain('caregroupApi/invalidateTags');
    const groupCall = dispatch.mock.calls.find(c => c[0]?.type === 'caregroupApi/invalidateTags');
    expect(groupCall?.[0]?.payload).toEqual(['Group']);
  });
});

describe('doseLogApiSlice 구조', () => {
  it('reducerPath = doseLogApi', () => {
    expect(doseLogApiSlice.reducerPath).toBe('doseLogApi');
  });

  it('checkDose endpoint 존재', () => {
    expect(doseLogApiSlice.endpoints.checkDose).toBeDefined();
  });

  it('useCheckDoseMutation hook export', () => {
    expect(typeof useCheckDoseMutation).toBe('function');
  });
});

describe('CheckDoseInput 타입', () => {
  it('TAKE action', () => {
    const input: CheckDoseInput = { doseLogId: 1, action: 'TAKE' };
    expect(input.action).toBe('TAKE');
    expect(input.doseLogId).toBe(1);
  });

  it('SKIP action + skipReason', () => {
    const input: CheckDoseInput = { doseLogId: 2, action: 'SKIP', skipReason: '부재중' };
    expect(input.action).toBe('SKIP');
    expect(input.skipReason).toBe('부재중');
  });

  it('skipReason 생략 가능', () => {
    const input: CheckDoseInput = { doseLogId: 3, action: 'TAKE' };
    expect(input.skipReason).toBeUndefined();
  });
});

describe('DoseStatus 값', () => {
  it('TAKEN 상태', () => {
    const s: DoseStatus = 'TAKEN';
    expect(['PENDING', 'TAKEN', 'SKIPPED', 'MISSED', 'DELAYED']).toContain(s);
  });

  it('FE done ↔ BE TAKEN, FE wait ↔ BE PENDING 매핑', () => {
    // FE 'done' → action 'TAKE' → BE saves TAKEN
    // FE 'wait' → action 'SKIP' → BE saves SKIPPED
    const takenAction: CheckDoseInput['action'] = 'TAKE';
    const skipAction: CheckDoseInput['action']  = 'SKIP';
    expect(takenAction).toBe('TAKE');
    expect(skipAction).toBe('SKIP');
  });
});
