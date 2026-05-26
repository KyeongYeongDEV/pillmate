import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';
import prescriptionFlow from './slices/prescriptionFlowSlice';
import home from './slices/homeSlice';
import { prescriptionApiSlice } from './slices/prescriptionApi';
import { drugApiSlice } from './slices/drugApi';
import { activityApi } from './slices/activityApi';
import { scheduleApiSlice } from './slices/scheduleApi';
import { chatApiSlice } from './slices/chatApi';
import { groupApi } from './slices/groupApi';

export const store = configureStore({
  reducer: {
    prescriptionFlow,
    home,
    [prescriptionApiSlice.reducerPath]: prescriptionApiSlice.reducer,
    [drugApiSlice.reducerPath]: drugApiSlice.reducer,
    [activityApi.reducerPath]: activityApi.reducer,
    [scheduleApiSlice.reducerPath]: scheduleApiSlice.reducer,
    [chatApiSlice.reducerPath]: chatApiSlice.reducer,
    [groupApi.reducerPath]: groupApi.reducer,
  },
  middleware: (getDefault) =>
    getDefault().concat(
      prescriptionApiSlice.middleware,
      drugApiSlice.middleware,
      activityApi.middleware,
      scheduleApiSlice.middleware,
      chatApiSlice.middleware,
      groupApi.middleware,
    ),
});

setupListeners(store.dispatch);

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
