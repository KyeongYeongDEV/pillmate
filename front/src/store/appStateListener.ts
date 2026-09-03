import { AppState } from 'react-native';
import type { setupListeners } from '@reduxjs/toolkit/query';

type SetupListenersCallback = NonNullable<Parameters<typeof setupListeners>[1]>;

// RTK Query 기본 리스너는 브라우저 window focus/visibilitychange 에 의존 — RN 엔 그 이벤트가 없어
// refetchOnFocus 가 무효가 된다. AppState 전환으로 대체 배선한다.
export const appStateFocusListener: SetupListenersCallback = (dispatch, { onFocus, onFocusLost }) => {
  const subscription = AppState.addEventListener('change', (nextState) => {
    dispatch(nextState === 'active' ? onFocus() : onFocusLost());
  });
  return () => subscription.remove();
};
