import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import type { ActivityFeedItem } from '@/types/activity';

export const activityApi = createApi({
  reducerPath: 'activityApi',
  baseQuery: createPillmateBaseQuery(),
  tagTypes: ['Activity'],
  endpoints: (build) => ({
    getRecentActivity: build.query<ActivityFeedItem[], void>({
      query: () => '/activity',
      transformResponse: (response: ApiEnvelope<ActivityFeedItem[]>) => response?.data ?? [],
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
