import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';
import prescriptionFlow from './slices/prescriptionFlowSlice';
import home from './slices/homeSlice';
import { prescriptionApiSlice } from './slices/prescriptionApi';
import { drugApiSlice } from './slices/drugApi';
import { activityApi } from './slices/activityApi';

export const store = configureStore({
  reducer: {
    prescriptionFlow,
    home,
    [prescriptionApiSlice.reducerPath]: prescriptionApiSlice.reducer,
    [drugApiSlice.reducerPath]: drugApiSlice.reducer,
    [activityApi.reducerPath]: activityApi.reducer,
  },
  middleware: (getDefault) =>
    getDefault().concat(
      prescriptionApiSlice.middleware,
      drugApiSlice.middleware,
      activityApi.middleware,
    ),
});

setupListeners(store.dispatch);

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
