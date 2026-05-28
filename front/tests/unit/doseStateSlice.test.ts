// RED: doseStateSlice.ts 미존재 → 전 항목 FAIL 예상
import doseStateReducer, { markDone, markWait, reset } from '@/store/slices/doseStateSlice';

describe('doseStateSlice', () => {
  it('초기 state empty', () => {
    expect(doseStateReducer(undefined, { type: '@@INIT' })).toEqual({});
  });

  it('markDone — state[doseLogId] = done', () => {
    const state = doseStateReducer({}, markDone({ doseLogId: 3 }));
    expect(state[3]).toBe('done');
  });

  it('markWait — state[doseLogId] = wait', () => {
    let state = doseStateReducer({}, markDone({ doseLogId: 3 }));
    state = doseStateReducer(state, markWait({ doseLogId: 3 }));
    expect(state[3]).toBe('wait');
  });

  it('reset — 전체 초기화', () => {
    let state = doseStateReducer({}, markDone({ doseLogId: 1 }));
    state = doseStateReducer(state, markDone({ doseLogId: 2 }));
    state = doseStateReducer(state, reset());
    expect(state).toEqual({});
  });

  it('다중 슬롯 독립 관리', () => {
    let state = doseStateReducer({}, markDone({ doseLogId: 1 }));
    state = doseStateReducer(state, markDone({ doseLogId: 2 }));
    state = doseStateReducer(state, markWait({ doseLogId: 1 }));
    expect(state[1]).toBe('wait');
    expect(state[2]).toBe('done');
  });
});
