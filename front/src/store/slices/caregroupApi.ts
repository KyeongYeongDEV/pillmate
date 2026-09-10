import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import type { MyGroupSummary, GroupDetailResponse, InviteCodeView } from '@/types/caregroup';
import type { ScheduleDay } from '@/types/schedule';
import type { PrescriptionDetailDrug, NutrientNote } from '@/types/prescription';

export interface CreateGroupResponse {
  groupId: number;
  name: string;
  role: string;
  inviteCode?: string;
}

// 이 그룹에서 내 약 이름을 볼 수 있으려면 구성원 스위치와 약봉투 스위치가 모두 켜져야 한다(AND).
export interface ShareMemberSetting {
  userId: number;
  name: string;
  role: string;
  shared: boolean;
}

export interface SharePrescriptionSetting {
  prescriptionId: number;
  label: string | null;
  prescribedAt: string;
  shared: boolean;
  status: 'ONGOING' | 'COMPLETED';
}

export interface ShareSettingsView {
  members: ShareMemberSetting[];
  prescriptions: SharePrescriptionSetting[];
  myColor: string | null;
}

const EMPTY_SHARE_SETTINGS: ShareSettingsView = { members: [], prescriptions: [], myColor: null };

export interface UpdateMemberShareArgs {
  groupId: number;
  viewerUserId: number;
  enabled: boolean;
}

export interface UpdatePrescriptionShareArgs {
  groupId: number;
  prescriptionId: number;
  enabled: boolean;
}

export interface NudgeMemberArgs {
  groupId: number;
  userId: number;
}

export interface NudgeMemberResult {
  alreadyNotified: boolean;
}

export interface GroupMemberAdherence {
  userId: number;
  adherence: 'FULL' | 'PARTIAL' | 'MISS' | 'UPCOMING';
}

export interface GroupMonthScheduleArg {
  groupId: number;
  month: string; // 'yyyy-MM'
}

interface GroupMonthScheduleResponse {
  month: string;
  days: { date: string; members: GroupMemberAdherence[] }[];
}

export interface GroupMemberDayView {
  userId: number;
  name: string;
  schedule: ScheduleDay;
}

export interface GroupDayScheduleArg {
  groupId: number;
  date: string; // yyyy-MM-dd
}

interface GroupDayScheduleResponse {
  date: string;
  members: GroupMemberDayView[];
}

// 공유받은 약봉투의 약 한 줄 — 개인용 상세와 같은 필드에 영양소 메모만 더해진다(약 정보만 공유).
export interface SharedPrescriptionDrug extends PrescriptionDetailDrug {
  nutrientNotes?: NutrientNote[] | null;
}

// 약 정보 공유 권한이 있는 구성원이 읽는 읽기 전용 뷰 — 처방전 원본 사진·메모·증상·AI 인사이트는 없다.
export interface SharedPrescriptionView {
  id: number;
  ownerUserId: number;
  prescribedAt: string;
  label: string | null;
  status: 'ONGOING' | 'COMPLETED';
  periodStart: string | null;
  periodEnd: string | null;
  daysRemaining: number | null;
  progressRate: number | null;
  adherenceRate: number | null;
  drugs: SharedPrescriptionDrug[];
}

export interface SharedPrescriptionArg {
  groupId: number;
  prescriptionId: number;
}

export const shareSettingsUrl = (groupId: number) => `/groups/${groupId}/share-settings`;

export const updateMemberShareRequest = ({ groupId, viewerUserId, enabled }: UpdateMemberShareArgs) => ({
  url: `/groups/${groupId}/share-settings/members/${viewerUserId}`,
  method: 'PUT' as const,
  body: { enabled },
});

export const updatePrescriptionShareRequest = ({ groupId, prescriptionId, enabled }: UpdatePrescriptionShareArgs) => ({
  url: `/groups/${groupId}/share-settings/prescriptions/${prescriptionId}`,
  method: 'PUT' as const,
  body: { enabled },
});

export function applyMemberShareToggle(
  view: ShareSettingsView,
  viewerUserId: number,
  enabled: boolean,
): void {
  const target = view.members.find((m) => m.userId === viewerUserId);
  if (target) target.shared = enabled;
}

export function applyPrescriptionShareToggle(
  view: ShareSettingsView,
  prescriptionId: number,
  enabled: boolean,
): void {
  const target = view.prescriptions.find((p) => p.prescriptionId === prescriptionId);
  if (target) target.shared = enabled;
}

const JOIN_TIMEOUT_MS = 10_000;

export const caregroupApiSlice = createApi({
  reducerPath: 'caregroupApi',
  baseQuery: createPillmateBaseQuery(),
  tagTypes: ['Group', 'GroupDetail', 'Activity', 'ShareSettings', 'GroupMonthSchedule', 'GroupDaySchedule', 'SharedPrescription'],
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
    renameGroup: build.mutation<void, { groupId: number; name: string }>({
      query: ({ groupId, name }) => ({ url: `/groups/${groupId}`, method: 'PATCH', body: { name } }),
      // 이름은 목록(Group)과 상세(GroupDetail) 양쪽에 보이므로 둘 다 무효화한다.
      invalidatesTags: (_result, _error, { groupId }) => [{ type: 'GroupDetail', id: groupId }, 'Group'],
    }),
    joinGroup: build.mutation<number, string>({
      query: (code) => ({ url: `/groups/join/${code}`, method: 'POST', timeout: JOIN_TIMEOUT_MS }),
      transformResponse: (response: ApiEnvelope<{ groupId: number }>) => response?.data?.groupId ?? 0,
      invalidatesTags: ['Group'],
    }),
    getShareSettings: build.query<ShareSettingsView, number>({
      query: (groupId) => shareSettingsUrl(groupId),
      transformResponse: (response: ApiEnvelope<ShareSettingsView>) => response?.data ?? EMPTY_SHARE_SETTINGS,
      providesTags: (_result, _error, groupId) => [{ type: 'ShareSettings', id: groupId }],
    }),
    updateMemberShare: build.mutation<void, UpdateMemberShareArgs>({
      query: (args) => updateMemberShareRequest(args),
      async onQueryStarted({ groupId, viewerUserId, enabled }, { dispatch, queryFulfilled }) {
        const patch = dispatch(
          caregroupApiSlice.util.updateQueryData('getShareSettings', groupId, (draft) => {
            applyMemberShareToggle(draft, viewerUserId, enabled);
          }),
        );
        try {
          await queryFulfilled;
        } catch {
          patch.undo();
        }
      },
      // 공유 대상 구성원 변경은 그룹 구성원이 보는 복약 현황을 바꾸므로 해당 그룹의 스케줄 캐시를 무효화한다.
      invalidatesTags: (_result, _error, { groupId }) => [
        { type: 'GroupMonthSchedule', id: groupId },
        { type: 'GroupDaySchedule', id: groupId },
      ],
    }),
    updatePrescriptionShare: build.mutation<void, UpdatePrescriptionShareArgs>({
      query: (args) => updatePrescriptionShareRequest(args),
      async onQueryStarted({ groupId, prescriptionId, enabled }, { dispatch, queryFulfilled }) {
        const patch = dispatch(
          caregroupApiSlice.util.updateQueryData('getShareSettings', groupId, (draft) => {
            applyPrescriptionShareToggle(draft, prescriptionId, enabled);
          }),
        );
        try {
          await queryFulfilled;
        } catch {
          patch.undo();
        }
      },
      // 공유 on/off 는 그룹 구성원이 보는 복약 현황을 바꾸므로 해당 그룹의 스케줄 캐시를 무효화한다.
      invalidatesTags: (_result, _error, { groupId }) => [
        { type: 'GroupMonthSchedule', id: groupId },
        { type: 'GroupDaySchedule', id: groupId },
      ],
    }),
    nudgeMember: build.mutation<NudgeMemberResult, NudgeMemberArgs>({
      query: ({ groupId, userId }) => ({ url: `/groups/${groupId}/members/${userId}/nudge`, method: 'POST' }),
      transformResponse: (response: ApiEnvelope<NudgeMemberResult>) =>
        response?.data ?? { alreadyNotified: false },
    }),
    getGroupMonthSchedule: build.query<Record<string, GroupMemberAdherence[]>, GroupMonthScheduleArg>({
      query: ({ groupId, month }) => `/groups/${groupId}/schedule/month?month=${month}`,
      transformResponse: (response: ApiEnvelope<GroupMonthScheduleResponse>) => {
        const map: Record<string, GroupMemberAdherence[]> = {};
        for (const day of response?.data?.days ?? []) map[day.date] = day.members;
        return map;
      },
      providesTags: (_r, _e, { groupId }) => [{ type: 'GroupMonthSchedule', id: groupId }],
    }),
    getGroupDaySchedule: build.query<GroupMemberDayView[], GroupDayScheduleArg>({
      query: ({ groupId, date }) => `/groups/${groupId}/schedule/day?date=${date}`,
      transformResponse: (response: ApiEnvelope<GroupDayScheduleResponse>) => response?.data?.members ?? [],
      providesTags: (_r, _e, { groupId, date }) => [
        { type: 'GroupDaySchedule', id: `${groupId}-${date}` },
        { type: 'GroupDaySchedule', id: groupId },
      ],
    }),
    getSharedPrescription: build.query<SharedPrescriptionView | null, SharedPrescriptionArg>({
      query: ({ groupId, prescriptionId }) => `/groups/${groupId}/prescriptions/${prescriptionId}`,
      transformResponse: (response: ApiEnvelope<SharedPrescriptionView>) => response?.data ?? null,
      providesTags: (_r, _e, { groupId, prescriptionId }) => [
        { type: 'SharedPrescription', id: `${groupId}-${prescriptionId}` },
      ],
    }),
    // 내 표시색은 전역(사용자) 설정이지만, 색이 바뀌면 이 슬라이스의 목록·모든 그룹 상세·현재 공유설정 캐시가
    // 전부 갱신돼야 한다. RTK Query 는 같은 슬라이스 안에서만 태그 무효화가 동작하므로 userApi 가 아니라
    // 여기(caregroupApi)에 두고, id 없이 타입만 지정해 그 타입의 모든 캐시(모든 groupId)를 한 번에 무효화한다.
    updateMyColor: build.mutation<void, string>({
      query: (color) => ({ url: '/users/me/color', method: 'PATCH', body: { color } }),
      invalidatesTags: ['Group', 'GroupDetail', 'ShareSettings'],
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
  useRenameGroupMutation,
  useJoinGroupMutation,
  useGetShareSettingsQuery,
  useUpdateMemberShareMutation,
  useUpdatePrescriptionShareMutation,
  useNudgeMemberMutation,
  useGetGroupMonthScheduleQuery,
  useGetGroupDayScheduleQuery,
  useGetSharedPrescriptionQuery,
  useUpdateMyColorMutation,
} = caregroupApiSlice;

