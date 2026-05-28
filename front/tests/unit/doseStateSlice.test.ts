// RED → GREEN: state shape changed to Record<doseLogId, { state, lockedAt? }>
import doseStateReducer, {
  markDone,
  markWait,
  markDoneNoLock,
  reset,
  selectIsLocked,
  LOCK_DURATION_MS,
} from '@/store/slices/doseStateSlice';

describe('doseStateSlice — 기본 상태', () => {
  it('초기 state empty', () => {
    expect(doseStateReducer(undefined, { type: '@@INIT' })).toEqual({});
  });

  it('markDone — state[doseLogId].state = done', () => {
    const state = doseStateReducer({}, markDone({ doseLogId: 3 }));
    expect(state[3].state).toBe('done');
  });

  it('markWait — state[doseLogId].state = wait', () => {
    let state = doseStateReducer({}, markDone({ doseLogId: 3 }));
    state = doseStateReducer(state, markWait({ doseLogId: 3 }));
    expect(state[3].state).toBe('wait');
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
    expect(state[1].state).toBe('wait');
    expect(state[2].state).toBe('done');
  });
});

describe('doseStateSlice — lockedAt + selectIsLocked', () => {
  it('markDone stores lockedAt', () => {
    const before = Date.now();
    const state = doseStateReducer({}, markDone({ doseLogId: 1 }));
    const after = Date.now();
    expect(state[1].lockedAt).toBeGreaterThanOrEqual(before);
    expect(state[1].lockedAt).toBeLessThanOrEqual(after);
  });

  it('markWait removes lockedAt', () => {
    let state = doseStateReducer({}, markDone({ doseLogId: 1 }));
    state = doseStateReducer(state, markWait({ doseLogId: 1 }));
    expect(state[1].lockedAt).toBeUndefined();
  });

  it('markDoneNoLock — done without lockedAt (BE revert)', () => {
    const state = doseStateReducer({}, markDoneNoLock({ doseLogId: 1 }));
    expect(state[1].state).toBe('done');
    expect(state[1].lockedAt).toBeUndefined();
  });

  it('selectIsLocked false within 60s', () => {
    const state = doseStateReducer({}, markDone({ doseLogId: 1 }));
    const now = (state[1].lockedAt ?? 0) + LOCK_DURATION_MS - 1;
    expect(selectIsLocked(state, 1, now)).toBe(false);
  });

  it('selectIsLocked true at exactly 60s', () => {
    const state = doseStateReducer({}, markDone({ doseLogId: 1 }));
    const now = (state[1].lockedAt ?? 0) + LOCK_DURATION_MS;
    expect(selectIsLocked(state, 1, now)).toBe(true);
  });

  it('selectIsLocked false for wait state (no lockedAt)', () => {
    let state = doseStateReducer({}, markDone({ doseLogId: 1 }));
    state = doseStateReducer(state, markWait({ doseLogId: 1 }));
    const now = Date.now() + LOCK_DURATION_MS * 2;
    expect(selectIsLocked(state, 1, now)).toBe(false);
  });

  it('selectIsLocked false for unknown doseLogId', () => {
    const state = doseStateReducer({}, markDone({ doseLogId: 1 }));
    expect(selectIsLocked(state, 99, Date.now() + LOCK_DURATION_MS)).toBe(false);
  });

  it('LOCK_DURATION_MS = 60_000', () => {
    expect(LOCK_DURATION_MS).toBe(60_000);
  });
});
