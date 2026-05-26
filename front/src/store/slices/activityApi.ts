import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { API_BASE_URL } from '@/lib/api/client';
import { getToken } from '@/lib/auth/storage';

export interface ActivityItem {
  id: number;
  actorName: string;
  actorEmoji: string;
  action: string;
  detail: string;
  occurredAt: string; // ISO 8601
}

export interface TodayProgress {
  taken: number;
  total: number;
  nextScheduleTime: string | null; // "12:00" format
  nextScheduleLabel: string | null;
}

export const activityApi = createApi({
  reducerPath: 'activityApi',
  baseQuery: fetchBaseQuery({
    baseUrl: API_BASE_URL,
    prepareHeaders: async (headers) => {
      const token = await getToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      headers.set('X-User-Id', '1'); // Phase 1 stub
      return headers;
    },
  }),
  endpoints: (build) => ({
    getRecentActivity: build.query<ActivityItem[], { groupId: number; limit?: number }>({
      query: ({ groupId, limit = 5 }) => `/groups/${groupId}/activity?limit=${limit}`,
      keepUnusedDataFor: 30,
    }),
    getTodayDoseProgress: build.query<TodayProgress, { patientId: number }>({
      query: ({ patientId }) => `/dose-logs/today?patientId=${patientId}`,
      keepUnusedDataFor: 60,
    }),
    getInsights: build.query<{ severity: 'INFO' | 'WARN' | 'CRITICAL'; message: string; detail: string } | null, { patientId: number }>({
      query: ({ patientId }) => `/reports/insights?patientId=${patientId}`,
      keepUnusedDataFor: 300,
    }),
  }),
});

export const {
  useGetRecentActivityQuery,
  useGetTodayDoseProgressQuery,
  useGetInsightsQuery,
} = activityApi;
