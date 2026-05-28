import { createSlice, PayloadAction } from '@reduxjs/toolkit';

export type DoseOverrideState = Record<number, 'done' | 'wait'>;

const doseStateSlice = createSlice({
  name: 'doseState',
  initialState: {} as DoseOverrideState,
  reducers: {
    markDone: (state, action: PayloadAction<{ doseLogId: number }>) => {
      state[action.payload.doseLogId] = 'done';
    },
    markWait: (state, action: PayloadAction<{ doseLogId: number }>) => {
      state[action.payload.doseLogId] = 'wait';
    },
    reset: () => ({} as DoseOverrideState),
  },
});

export const { markDone, markWait, reset } = doseStateSlice.actions;
export default doseStateSlice.reducer;
