import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { CheckDoseInput, DoseLogResponse } from '@/types/doseLog';
import { markDone, markWait, markDoneNoLock } from './doseStateSlice';
import { scheduleApiSlice } from './scheduleApi';

export const doseLogApiSlice = createApi({
  reducerPath: 'doseLogApi',
  baseQuery: createPillmateBaseQuery(),
  tagTypes: ['Schedule', 'DoseLog', 'Activity'],
  endpoints: (build) => ({
    checkDose: build.mutation<DoseLogResponse, CheckDoseInput>({
      query: ({ skipOptimistic: _skipOptimistic, ...body }) => ({
        url: '/dose-logs/check',
        method: 'PATCH',
        body,
      }),
      invalidatesTags: ['Schedule', 'DoseLog', 'Activity'],
      async onQueryStarted({ doseLogId, action, skipOptimistic }, { dispatch, queryFulfilled }) {
        if (!skipOptimistic) {
          dispatch(action === 'TAKE' ? markDone({ doseLogId }) : markWait({ doseLogId }));
        }
        try {
          await queryFulfilled;
          dispatch(scheduleApiSlice.util.invalidateTags(['MonthSchedule']));
        } catch {
          // Revert on network failure; markDoneNoLock avoids restarting the 60s timer
          if (!skipOptimistic) {
            dispatch(action === 'TAKE' ? markWait({ doseLogId }) : markDoneNoLock({ doseLogId }));
          }
        }
      },
    }),
  }),
});

export const { useCheckDoseMutation } = doseLogApiSlice;
