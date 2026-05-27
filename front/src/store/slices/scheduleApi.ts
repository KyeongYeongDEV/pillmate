import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { API_BASE_URL } from '@/lib/api/client';
import { getToken } from '@/lib/auth/storage';
import type { ScheduleDay } from '@/types/schedule';

const MOCK_SCHEDULE: ScheduleDay = {
  date: '2025-11-24',
  totalCount: 6,
  doneCount: 4,
  slots: [
    { id: 'morning', time: '08:00', label: '아침',    state: 'done', items: ['암로디핀 5mg', '메트포르민 500mg'], doseLogId: 101 },
    { id: 'noon',    time: '12:30', label: '점심',    state: 'now',  items: ['메트포르민 500mg', '글리메피리드 2mg'], doseLogId: 102 },
    { id: 'evening', time: '19:00', label: '저녁',    state: 'wait', items: ['아토르바스타틴 10mg'], doseLogId: 103 },
    { id: 'bedtime', time: '22:00', label: '취침 전', state: 'wait', items: ['오메가-3 1000mg'], doseLogId: 104 },
  ],
};

export const scheduleApiSlice = createApi({
  reducerPath: 'scheduleApi',
  baseQuery: fetchBaseQuery({
    baseUrl: API_BASE_URL,
    prepareHeaders: async (headers) => {
      const token = await getToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      headers.set('X-User-Id', '1');
      return headers;
    },
  }),
  tagTypes: ['Schedule'],
  keepUnusedDataFor: 30,
  endpoints: (build) => ({
    getDaySchedule: build.query<ScheduleDay, string>({
      // Phase 2: replace queryFn with query: (date) => `/schedules/day?date=${date}`
      queryFn: async () => ({ data: MOCK_SCHEDULE }),
      providesTags: ['Schedule'],
    }),
  }),
});

export const { useGetDayScheduleQuery } = scheduleApiSlice;
