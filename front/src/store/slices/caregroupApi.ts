import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import type { MyGroupSummary, GroupDetailResponse, InviteCodeView } from '@/types/caregroup';

export interface CreateGroupResponse {
  groupId: number;
  name: string;
  role: string;
  inviteCode?: string;
}

const JOIN_TIMEOUT_MS = 10_000;

export const caregroupApiSlice = createApi({
  reducerPath: 'caregroupApi',
  baseQuery: createPillmateBaseQuery(),
  tagTypes: ['Group', 'GroupDetail', 'Activity'],
  endpoints: (build) => ({
    getMyGroups: build.query<MyGroupSummary[], void>({
      query: () => '/groups',
      transformResponse: (response: ApiEnvelope<MyGroupSummary[]>) => response?.data ?? [],
      providesTags: ['Group'],
    }),
    getGroupDetail: build.query<GroupDetailResponse | null, number>({
      query: (id) => `/groups/${id}`,
      transformResponse: (response: ApiEnvelope<GroupDetailResponse>) => response?.data ?? null,
      providesTags: (_result, _error, id) => [{ type: 'GroupDetail', id }],
    }),
    pinGroup: build.mutation<void, number>({
      query: (id) => ({ url: `/groups/${id}/pin`, method: 'POST' }),
      // BE PinGroupUseCase 단일 핀 — 새 핀 시 기존 해제
      async onQueryStarted(id, { dispatch, queryFulfilled }) {
        const patch = dispatch(
          caregroupApiSlice.util.updateQueryData('getMyGroups', undefined, (draft) => {
            draft.forEach(g => { g.pinned = g.groupId === id; });
          }),
        );
        try {
          await queryFulfilled;
        } catch {
          patch.undo();
        }
      },
      invalidatesTags: ['Group'],
    }),
    unpinGroup: build.mutation<void, number>({
      query: (id) => ({ url: `/groups/${id}/pin`, method: 'DELETE' }),
      invalidatesTags: ['Group'],
    }),
    issueInviteCode: build.mutation<InviteCodeView | null, number>({
      query: (groupId) => ({ url: `/groups/${groupId}/invite-codes`, method: 'POST' }),
      transformResponse: (response: ApiEnvelope<InviteCodeView>) => response?.data ?? null,
      invalidatesTags: (_result, _error, groupId) => [{ type: 'GroupDetail', id: groupId }],
    }),
    createGroup: build.mutation<CreateGroupResponse | null, { name: string }>({
      query: (body) => ({ url: '/groups', method: 'POST', body }),
      transformResponse: (response: ApiEnvelope<CreateGroupResponse>) => response?.data ?? null,
      invalidatesTags: ['Group'],
    }),
    leaveGroup: build.mutation<void, number>({
      query: (groupId) => ({ url: `/groups/${groupId}/membership`, method: 'DELETE' }),
      invalidatesTags: ['Group'],
    }),
    joinGroup: build.mutation<number, string>({
      query: (code) => ({ url: `/groups/join/${code}`, method: 'POST', timeout: JOIN_TIMEOUT_MS }),
      transformResponse: (response: ApiEnvelope<{ groupId: number }>) => response?.data?.groupId ?? 0,
      invalidatesTags: ['Group'],
    }),
  }),
});

export const {
  useGetMyGroupsQuery,
  useGetGroupDetailQuery,
  usePinGroupMutation,
  useUnpinGroupMutation,
  useIssueInviteCodeMutation,
  useCreateGroupMutation,
  useLeaveGroupMutation,
  useJoinGroupMutation,
} = caregroupApiSlice;

