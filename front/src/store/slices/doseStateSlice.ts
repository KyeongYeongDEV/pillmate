import { createSlice, PayloadAction } from '@reduxjs/toolkit';

// OCR_MIN_CONFIDENCE analogue: grace period before group notification fires
export const LOCK_DURATION_MS = 60_000;

export interface DoseEntry {
  state: 'done' | 'wait';
  lockedAt?: number;
}

export type DoseOverrideState = Record<number, DoseEntry>;

const doseStateSlice = createSlice({
  name: 'doseState',
  initialState: {} as DoseOverrideState,
  reducers: {
    markDone: (state, action: PayloadAction<{ doseLogId: number }>) => {
      state[action.payload.doseLogId] = { state: 'done', lockedAt: Date.now() };
    },
    markWait: (state, action: PayloadAction<{ doseLogId: number }>) => {
      state[action.payload.doseLogId] = { state: 'wait' };
    },
    // Used by onQueryStarted revert only — restores done without starting a new timer
    markDoneNoLock: (state, action: PayloadAction<{ doseLogId: number }>) => {
      state[action.payload.doseLogId] = { state: 'done' };
    },
    reset: () => ({} as DoseOverrideState),
  },
});

export const { markDone, markWait, markDoneNoLock, reset } = doseStateSlice.actions;

export const selectIsLocked = (
  state: DoseOverrideState,
  doseLogId: number,
  now: number,
): boolean => {
  const entry = state[doseLogId];
  if (!entry || entry.lockedAt == null) return false;
  return now - entry.lockedAt >= LOCK_DURATION_MS;
};

export default doseStateSlice.reducer;
