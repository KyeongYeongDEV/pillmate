import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';
import prescriptionFlow from './slices/prescriptionFlowSlice';
import home from './slices/homeSlice';
import doseState from './slices/doseStateSlice';
import { notifyGroupTimerMiddleware } from './middleware/notifyGroupTimer';
import { prescriptionApiSlice } from './slices/prescriptionApi';
import { drugApiSlice } from './slices/drugApi';
import { activityApi } from './slices/activityApi';
import { scheduleApiSlice } from './slices/scheduleApi';
import { chatApiSlice } from './slices/chatApi';
import { doseLogApiSlice } from './slices/doseLogApi';
import { caregroupApiSlice } from './slices/caregroupApi';
import { userApiSlice } from './slices/userApi';

export const store = configureStore({
  reducer: {
    prescriptionFlow,
    home,
    doseState,
    [prescriptionApiSlice.reducerPath]: prescriptionApiSlice.reducer,
    [drugApiSlice.reducerPath]: drugApiSlice.reducer,
    [activityApi.reducerPath]: activityApi.reducer,
    [scheduleApiSlice.reducerPath]: scheduleApiSlice.reducer,
    [chatApiSlice.reducerPath]: chatApiSlice.reducer,
    [doseLogApiSlice.reducerPath]: doseLogApiSlice.reducer,
    [caregroupApiSlice.reducerPath]: caregroupApiSlice.reducer,
    [userApiSlice.reducerPath]: userApiSlice.reducer,
  },
  middleware: (getDefault) =>
    getDefault().concat(
      prescriptionApiSlice.middleware,
      drugApiSlice.middleware,
      activityApi.middleware,
      scheduleApiSlice.middleware,
      chatApiSlice.middleware,
      doseLogApiSlice.middleware,
      notifyGroupTimerMiddleware,
      caregroupApiSlice.middleware,
      userApiSlice.middleware,
    ),
});

setupListeners(store.dispatch);

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
