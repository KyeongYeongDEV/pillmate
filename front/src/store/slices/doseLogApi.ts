import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { API_BASE_URL } from '@/lib/api/client';
import { getToken } from '@/lib/auth/storage';
import type { CheckDoseInput, DoseLogResponse } from '@/types/doseLog';
import { markDone, markWait, markDoneNoLock } from './doseStateSlice';

export const doseLogApiSlice = createApi({
  reducerPath: 'doseLogApi',
  baseQuery: fetchBaseQuery({
    baseUrl: API_BASE_URL,
    prepareHeaders: async (headers) => {
      const token = await getToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      // Phase 1 dummy user header — replaced by real JWT in auth integration
      headers.set('X-User-Id', '1');
      return headers;
    },
  }),
  tagTypes: ['Schedule', 'DoseLog', 'Activity'],
  endpoints: (build) => ({
    checkDose: build.mutation<DoseLogResponse, CheckDoseInput>({
      query: (body) => ({
        url: '/dose-logs/check',
        method: 'PATCH',
        body,
      }),
      invalidatesTags: ['Schedule', 'DoseLog', 'Activity'],
      async onQueryStarted({ doseLogId, action }, { dispatch, queryFulfilled }) {
        dispatch(action === 'TAKE' ? markDone({ doseLogId }) : markWait({ doseLogId }));
        try {
          await queryFulfilled;
        } catch {
          // Revert on network failure; markDoneNoLock avoids restarting the 60s timer
          dispatch(action === 'TAKE' ? markWait({ doseLogId }) : markDoneNoLock({ doseLogId }));
        }
      },
    }),

    // T-BE-NOTIFY-GROUP pending: swallow 404/501 and warn
    notifyGroup: build.mutation<void, number>({
      query: (doseLogId) => ({
        url: `/dose-logs/${doseLogId}/notify-group`,
        method: 'POST',
      }),
      async onQueryStarted(doseLogId, { queryFulfilled }) {
        try {
          await queryFulfilled;
        } catch (err: any) {
          const status = err?.error?.status;
          if (status === 404 || status === 501) {
            console.warn(`[notifyGroup] BE endpoint not yet implemented (${status}) for doseLogId=${doseLogId}`);
          } else {
            console.warn(`[notifyGroup] unexpected error for doseLogId=${doseLogId}`, err);
          }
        }
      },
    }),
  }),
});

export const { useCheckDoseMutation, useNotifyGroupMutation } = doseLogApiSlice;
