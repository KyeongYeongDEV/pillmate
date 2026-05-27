import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { API_BASE_URL } from '@/lib/api/client';
import { getToken } from '@/lib/auth/storage';
import type { CheckDoseInput, DoseLogResponse } from '@/types/doseLog';

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
    }),
  }),
});

export const { useCheckDoseMutation } = doseLogApiSlice;
