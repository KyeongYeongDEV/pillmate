import { combineReducers, configureStore, Reducer } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';
import { persistReducer, persistStore, FLUSH, REHYDRATE, PAUSE, PERSIST, PURGE, REGISTER } from 'redux-persist';
import prescriptionFlow from './slices/prescriptionFlowSlice';
import doseState from './slices/doseStateSlice';
import cacheMeta from './slices/cacheMetaSlice';
import { prescriptionApiSlice } from './slices/prescriptionApi';
import { drugApiSlice } from './slices/drugApi';
import { activityApi } from './slices/activityApi';
import { scheduleApiSlice } from './slices/scheduleApi';
import { chatApiSlice } from './slices/chatApi';
import { doseLogApiSlice } from './slices/doseLogApi';
import { caregroupApiSlice } from './slices/caregroupApi';
import { userApiSlice } from './slices/userApi';
import { notificationApiSlice } from './slices/notificationApi';
import { authApiSlice } from './slices/authApi';
import { persistConfig, cacheDateTagMiddleware } from './persistConfig';

const rootReducer = combineReducers({
  prescriptionFlow,
  doseState,
  cacheMeta,
  [prescriptionApiSlice.reducerPath]: prescriptionApiSlice.reducer,
  [drugApiSlice.reducerPath]: drugApiSlice.reducer,
  [activityApi.reducerPath]: activityApi.reducer,
  [scheduleApiSlice.reducerPath]: scheduleApiSlice.reducer,
  [chatApiSlice.reducerPath]: chatApiSlice.reducer,
  [doseLogApiSlice.reducerPath]: doseLogApiSlice.reducer,
  [caregroupApiSlice.reducerPath]: caregroupApiSlice.reducer,
  [userApiSlice.reducerPath]: userApiSlice.reducer,
  [notificationApiSlice.reducerPath]: notificationApiSlice.reducer,
  [authApiSlice.reducerPath]: authApiSlice.reducer,
});

type RootReducerState = ReturnType<typeof rootReducer>;

// redux-persist 의 번들 타입(구 2-generic Reducer)과 RTK 5.x 의 3-generic Reducer 간 구조적 불일치 —
// 런타임에는 무관(순수 함수 결합)하므로 호출부에서만 캐스트.
const persistedReducer = persistReducer(
  persistConfig,
  rootReducer as unknown as Reducer<RootReducerState>,
);

export const store = configureStore({
  reducer: persistedReducer,
  middleware: (getDefault) =>
    getDefault({
      serializableCheck: {
        ignoredActions: [FLUSH, REHYDRATE, PAUSE, PERSIST, PURGE, REGISTER],
      },
    }).concat(
      prescriptionApiSlice.middleware,
      drugApiSlice.middleware,
      activityApi.middleware,
      scheduleApiSlice.middleware,
      chatApiSlice.middleware,
      doseLogApiSlice.middleware,
      caregroupApiSlice.middleware,
      userApiSlice.middleware,
      notificationApiSlice.middleware,
      authApiSlice.middleware,
      cacheDateTagMiddleware,
    ),
});

export const persistor = persistStore(store);

setupListeners(store.dispatch);

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
