import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';
import prescriptionFlow from './slices/prescriptionFlowSlice';
import { prescriptionApiSlice } from './slices/prescriptionApi';
import { drugApiSlice } from './slices/drugApi';

export const store = configureStore({
  reducer: {
    prescriptionFlow,
    [prescriptionApiSlice.reducerPath]: prescriptionApiSlice.reducer,
    [drugApiSlice.reducerPath]: drugApiSlice.reducer,
  },
  middleware: (getDefault) =>
    getDefault().concat(prescriptionApiSlice.middleware, drugApiSlice.middleware),
});

setupListeners(store.dispatch);

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
