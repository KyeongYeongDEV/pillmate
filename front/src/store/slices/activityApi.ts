import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import type { ActivityFeedItem } from '@/types/activity';

export interface PraiseResult {
  alreadyPraised: boolean;
}

export const activityApi = createApi({
  reducerPath: 'activityApi',
  baseQuery: createPillmateBaseQuery(),
  tagTypes: ['Activity'],
  endpoints: (build) => ({
    getRecentActivity: build.query<ActivityFeedItem[], { groupId?: number; limit?: number } | void>({
      query: (arg) => {
        const groupId = arg && 'groupId' in arg ? arg.groupId : undefined;
        const limit = arg && 'limit' in arg ? arg.limit : undefined;
        const params = new URLSearchParams();
        if (groupId != null) params.set('groupId', String(groupId));
        if (limit != null) params.set('limit', String(limit));
        const qs = params.toString();
        return qs ? `/activity?${qs}` : '/activity';
      },
      transformResponse: (response: ApiEnvelope<ActivityFeedItem[]>) => response?.data ?? [],
      providesTags: ['Activity'],
      keepUnusedDataFor: 30,
    }),
    // "복용 완료" 활동에 칭찬 보내기 — 서버가 (activity, praiser) 유니크로 중복 알림을 막아주므로
    // 재요청해도 안전(alreadyPraised=true 로 응답).
    praiseActivity: build.mutation<PraiseResult, { activityFeedId: number; groupId: number }>({
      query: ({ activityFeedId, groupId }) => ({
        url: `/activity/${activityFeedId}/praise?groupId=${groupId}`,
        method: 'POST',
      }),
      transformResponse: (response: ApiEnvelope<PraiseResult>) =>
        response?.data ?? { alreadyPraised: false },
    }),
  }),
});

export const { useGetRecentActivityQuery, usePraiseActivityMutation } = activityApi;

// TodayProgressCard에서 참조 (Phase 2에서 별도 API로 분리 예정)
export interface TodayProgress {
  taken: number;
  total: number;
  nextScheduleTime: string | null;
  nextScheduleLabel: string | null;
}
