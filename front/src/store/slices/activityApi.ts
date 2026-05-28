import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { API_BASE_URL } from '@/lib/api/client';
import type { ApiEnvelope } from '@/lib/api/client';
import { getToken } from '@/lib/auth/storage';
import type { ActivityFeedItem } from '@/types/activity';

export const activityApi = createApi({
  reducerPath: 'activityApi',
  baseQuery: fetchBaseQuery({
    baseUrl: API_BASE_URL,
    prepareHeaders: async (headers) => {
      const token = await getToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      headers.set('X-User-Id', '1');
      return headers;
    },
  }),
  tagTypes: ['Activity'],
  endpoints: (build) => ({
    getRecentActivity: build.query<ActivityFeedItem[], void>({
      query: () => '/activity',
      transformResponse: (response: ApiEnvelope<ActivityFeedItem[]>) => response.data ?? [],
      providesTags: ['Activity'],
      keepUnusedDataFor: 30,
    }),
  }),
});

export const { useGetRecentActivityQuery } = activityApi;

// TodayProgressCard에서 참조 (Phase 2에서 별도 API로 분리 예정)
export interface TodayProgress {
  taken: number;
  total: number;
  nextScheduleTime: string | null;
  nextScheduleLabel: string | null;
}
