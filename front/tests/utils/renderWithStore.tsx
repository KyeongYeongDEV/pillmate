import React from 'react';
import { render, RenderOptions } from '@testing-library/react-native';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { scheduleApiSlice } from '@/store/slices/scheduleApi';
import { doseLogApiSlice } from '@/store/slices/doseLogApi';
import { activityApi } from '@/store/slices/activityApi';

function makeTestStore() {
  return configureStore({
    reducer: {
      [scheduleApiSlice.reducerPath]: scheduleApiSlice.reducer,
      [doseLogApiSlice.reducerPath]: doseLogApiSlice.reducer,
      [activityApi.reducerPath]: activityApi.reducer,
    },
    middleware: (getDefault) =>
      getDefault().concat(
        scheduleApiSlice.middleware,
        doseLogApiSlice.middleware,
        activityApi.middleware,
      ),
  });
}

export function renderWithStore(ui: React.ReactElement, options?: RenderOptions) {
  const store = makeTestStore();
  return render(
    <Provider store={store}>{ui}</Provider>,
    options,
  );
}
