import type { Middleware } from '@reduxjs/toolkit';
import { markDone, markWait, LOCK_DURATION_MS } from '../slices/doseStateSlice';
import { doseLogApiSlice } from '../slices/doseLogApi';

// Global map survives screen unmounts — timer fires even if user navigates away
const timerMap = new Map<number, ReturnType<typeof setTimeout>>();

export const notifyGroupTimerMiddleware: Middleware = (storeAPI) => (next) => (action) => {
  const result = next(action);

  if (markDone.match(action)) {
    const { doseLogId } = action.payload;
    const existing = timerMap.get(doseLogId);
    if (existing != null) clearTimeout(existing);

    const timerId = setTimeout(() => {
      timerMap.delete(doseLogId);
      storeAPI.dispatch(
        doseLogApiSlice.endpoints.notifyGroup.initiate(doseLogId) as any,
      );
    }, LOCK_DURATION_MS);
    timerMap.set(doseLogId, timerId);
  }

  if (markWait.match(action)) {
    const { doseLogId } = action.payload;
    const existing = timerMap.get(doseLogId);
    if (existing != null) {
      clearTimeout(existing);
      timerMap.delete(doseLogId);
    }
  }

  return result;
};

// Exposed for tests only
export const _timerMap = timerMap;
