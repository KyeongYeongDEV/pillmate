import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ScheduleDay } from '@/types/schedule';

const MOCK_SCHEDULE: ScheduleDay = {
  date: '2025-11-24',
  totalCount: 6,
  doneCount: 4,
  slots: [
    { id: 'morning', time: '08:00', label: '아침',    state: 'done', items: ['암로디핀 5mg', '메트포르민 500mg'], doseLogId: 4 },
    { id: 'noon',    time: '12:30', label: '점심',    state: 'now',  items: ['메트포르민 500mg', '글리메피리드 2mg'], doseLogId: 5 },
    { id: 'evening', time: '19:00', label: '저녁',    state: 'wait', items: ['아토르바스타틴 10mg'], doseLogId: 6 },
    { id: 'bedtime', time: '22:00', label: '취침 전', state: 'wait', items: ['오메가-3 1000mg'], doseLogId: 7 },
  ],
};

export const scheduleApiSlice = createApi({
  reducerPath: 'scheduleApi',
  baseQuery: createPillmateBaseQuery(),
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
