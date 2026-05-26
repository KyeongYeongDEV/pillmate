import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface HomeState {
  activeGroupId: number | null;
  unreadCount: number;
}

const initialState: HomeState = {
  activeGroupId: null,
  unreadCount: 4, // 디자인 mock 일치 — Phase 2에서 서버 값으로 교체
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
