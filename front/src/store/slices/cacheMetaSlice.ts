import { createSlice, PayloadAction } from '@reduxjs/toolkit';

export interface CacheMetaState {
  savedDateKst: string | null;
}

const cacheMetaSlice = createSlice({
  name: 'cacheMeta',
  initialState: { savedDateKst: null } as CacheMetaState,
  reducers: {
    setSavedDateKst: (state, action: PayloadAction<string>) => {
      state.savedDateKst = action.payload;
    },
  },
});

export const { setSavedDateKst } = cacheMetaSlice.actions;
export default cacheMetaSlice.reducer;
