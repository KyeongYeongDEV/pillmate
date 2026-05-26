import { configureStore } from '@reduxjs/toolkit';
import homeReducer, {
  setActiveGroup,
  setUnreadCount,
  incrementUnread,
  clearUnread,
} from '@/store/slices/homeSlice';

function makeStore() {
  return configureStore({ reducer: { home: homeReducer } });
}

describe('homeSlice', () => {
  it('초기 상태: activeGroupId=null, unreadCount=0', () => {
    const store = makeStore();
    const state = store.getState().home;
    expect(state.activeGroupId).toBeNull();
    expect(state.unreadCount).toBe(0);
  });

  it('setActiveGroup: 그룹 ID 설정', () => {
    const store = makeStore();
    store.dispatch(setActiveGroup(42));
    expect(store.getState().home.activeGroupId).toBe(42);
  });

  it('setActiveGroup: null로 초기화', () => {
    const store = makeStore();
    store.dispatch(setActiveGroup(42));
    store.dispatch(setActiveGroup(null));
    expect(store.getState().home.activeGroupId).toBeNull();
  });

  it('setUnreadCount: 미읽음 수 설정', () => {
    const store = makeStore();
    store.dispatch(setUnreadCount(7));
    expect(store.getState().home.unreadCount).toBe(7);
  });

  it('incrementUnread: 1씩 증가', () => {
    const store = makeStore();
    store.dispatch(incrementUnread());
    store.dispatch(incrementUnread());
    expect(store.getState().home.unreadCount).toBe(2);
  });

  it('clearUnread: 0으로 초기화', () => {
    const store = makeStore();
    store.dispatch(setUnreadCount(5));
    store.dispatch(clearUnread());
    expect(store.getState().home.unreadCount).toBe(0);
  });
});
