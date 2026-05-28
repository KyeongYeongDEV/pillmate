import { configureStore } from '@reduxjs/toolkit';
import doseState from '@/store/slices/doseStateSlice';
import { doseLogApiSlice } from '@/store/slices/doseLogApi';
import { notifyGroupTimerMiddleware, _timerMap } from '@/store/middleware/notifyGroupTimer';
import { markDone, markWait, LOCK_DURATION_MS } from '@/store/slices/doseStateSlice';

function makeTestStore() {
  return configureStore({
    reducer: {
      doseState,
      [doseLogApiSlice.reducerPath]: doseLogApiSlice.reducer,
    },
    middleware: (getDefault) =>
      getDefault()
        .concat(doseLogApiSlice.middleware)
        .concat(notifyGroupTimerMiddleware),
  });
}

describe('notifyGroupTimerMiddleware', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    _timerMap.clear();
  });

  afterEach(() => {
    jest.useRealTimers();
    _timerMap.clear();
  });

  it('markDone → timer 등록', () => {
    const store = makeTestStore();
    store.dispatch(markDone({ doseLogId: 1 }));
    expect(_timerMap.has(1)).toBe(true);
  });

  it('markWait → timer 취소', () => {
    const store = makeTestStore();
    store.dispatch(markDone({ doseLogId: 1 }));
    expect(_timerMap.has(1)).toBe(true);
    store.dispatch(markWait({ doseLogId: 1 }));
    expect(_timerMap.has(1)).toBe(false);
  });

  it('60초 이내 취소 → 타이머 소멸, dispatch 추가 없음', () => {
    const store = makeTestStore();
    const spy = jest.spyOn(store, 'dispatch');
    store.dispatch(markDone({ doseLogId: 2 }));
    jest.advanceTimersByTime(30_000);
    store.dispatch(markWait({ doseLogId: 2 }));
    jest.advanceTimersByTime(LOCK_DURATION_MS);
    // Only markDone + markWait dispatched, no notifyGroup
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('60초 경과 → timer 정리 (callback 실행 확인)', () => {
    const store = makeTestStore();
    store.dispatch(markDone({ doseLogId: 3 }));
    expect(_timerMap.has(3)).toBe(true);
    jest.advanceTimersByTime(LOCK_DURATION_MS);
    // timer callback 실행 → timerMap 에서 제거됨
    expect(_timerMap.has(3)).toBe(false);
  });

  it('중복 markDone → 이전 timer 교체', () => {
    const store = makeTestStore();
    store.dispatch(markDone({ doseLogId: 4 }));
    const firstTimer = _timerMap.get(4);
    store.dispatch(markDone({ doseLogId: 4 }));
    const secondTimer = _timerMap.get(4);
    expect(secondTimer).not.toBe(firstTimer);
  });

  it('다른 doseLogId는 독립 timer', () => {
    const store = makeTestStore();
    store.dispatch(markDone({ doseLogId: 5 }));
    store.dispatch(markDone({ doseLogId: 6 }));
    store.dispatch(markWait({ doseLogId: 5 }));
    expect(_timerMap.has(5)).toBe(false);
    expect(_timerMap.has(6)).toBe(true);
  });
});
