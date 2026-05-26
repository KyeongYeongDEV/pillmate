import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface HomeState {
  activeGroupId: number | null;
  unreadCount: number;
}

const initialState: HomeState = {
  activeGroupId: null,
  unreadCount: 0,
};

const homeSlice = createSlice({
  name: 'home',
  initialState,
  reducers: {
    setActiveGroup(state, action: PayloadAction<number | null>) {
      state.activeGroupId = action.payload;
    },
    setUnreadCount(state, action: PayloadAction<number>) {
      state.unreadCount = action.payload;
    },
    incrementUnread(state) {
      state.unreadCount += 1;
    },
    clearUnread(state) {
      state.unreadCount = 0;
    },
  },
});

export const { setActiveGroup, setUnreadCount, incrementUnread, clearUnread } = homeSlice.actions;
export default homeSlice.reducer;
