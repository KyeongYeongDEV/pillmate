import { createApi, fakeBaseQuery } from '@reduxjs/toolkit/query/react';
import type { ActivityFeedItem } from '@/types/activity';

const MOCK_FEED: ActivityFeedItem[] = [
  {
    id: 1,
    actorUserId: 2,
    actorName: '박순자',
    activityType: 'DOSE_TAKEN',
    summary: '아침약 2개를 복용했어요',
    severity: 'INFO',
    occurredAt: new Date(Date.now() - 3 * 60 * 1000).toISOString(),
  },
  {
    id: 2,
    actorUserId: 1,
    actorName: '나',
    activityType: 'PRESCRIPTION_REGISTERED',
    summary: '새 처방전을 등록했어요',
    severity: 'INFO',
    occurredAt: new Date(Date.now() - 26 * 60 * 1000).toISOString(),
  },
  {
    id: 3,
    actorUserId: 2,
    actorName: '박순자',
    activityType: 'DOSE_MISSED',
    summary: '취침 전 약을 놓치셨어요',
    severity: 'WARN',
    occurredAt: new Date(Date.now() - 8 * 60 * 60 * 1000).toISOString(),
  },
];

// Phase 2: BE GET /api/v1/activity 연결 시 fetchBaseQuery + real endpoint 로 교체
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

// Legacy types — backward compat (Phase 2 삭제 예정)
export interface ActivityItem {
  id: number;
  actorName: string;
  actorEmoji: string;
  action: string;
  detail: string;
  occurredAt: string;
}

export interface TodayProgress {
  taken: number;
  total: number;
  nextScheduleTime: string | null;
  nextScheduleLabel: string | null;
}
