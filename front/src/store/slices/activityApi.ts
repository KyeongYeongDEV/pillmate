import { createApi, fakeBaseQuery } from '@reduxjs/toolkit/query/react';
import type { ActivityFeedItem } from '@/types/activity';

const MOCK_FEED: ActivityFeedItem[] = [
  {
    actorNickname: '할머니',
    activityType: 'DOSE_TAKEN',
    timeSlot: 'MORNING',
    summary: '아침약 2개를 복용했어요',
    severity: 'INFO',
    occurredAt: new Date(Date.now() - 3 * 60 * 1000).toISOString(),
  },
  {
    actorNickname: '아들',
    activityType: 'DOSE_MISSED',
    timeSlot: 'BEDTIME',
    summary: '취침 전 약을 놓치셨어요',
    severity: 'WARN',
    occurredAt: new Date(Date.now() - 8 * 60 * 60 * 1000).toISOString(),
  },
  {
    actorNickname: '할머니',
    activityType: 'DOSE_TAKEN',
    timeSlot: 'NOON',
    summary: '점심약 3개를 복용했어요',
    severity: 'INFO',
    occurredAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
  },
];

// Phase 2: BE GET /api/v1/activity 연결 시 fetchBaseQuery + real endpoint 교체
export const activityApi = createApi({
  reducerPath: 'activityApi',
  baseQuery: fakeBaseQuery(),
  endpoints: (build) => ({
    getRecentActivity: build.query<ActivityFeedItem[], void>({
      queryFn: () => ({ data: MOCK_FEED }),
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
