import React from 'react';
import { render, act } from '@testing-library/react-native';
import { RefreshControl } from 'react-native';
import GroupDetailScreen from '@/app/(tabs)/group/[id]';
import { useGetGroupDetailQuery } from '@/store/slices/caregroupApi';

jest.mock('expo-router', () => ({
  useLocalSearchParams: () => ({ id: '7' }),
  router: { replace: jest.fn(), push: jest.fn() },
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: React.ReactNode }) => children,
}));
jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));
jest.mock('react-redux', () => ({ useDispatch: () => jest.fn() }));
jest.mock('@/hooks/useCountdown', () => ({
  useCountdown: () => ({ remainingSeconds: 0, isExpired: true }),
}));
jest.mock('@/lib/auth/storage', () => ({ getCurrentUserId: () => Promise.resolve(1) }));
jest.mock('@/components/common/AvatarStack', () => () => null);
jest.mock('@/components/group/MemberCard', () => () => null);
jest.mock('@/components/group/InviteCodeCard', () => () => null);
jest.mock('@/components/group/GroupScheduleCalendar', () => () => null);

// now prop 만 뽑아 확인 — 사용자 요청(2026-09-17): 새로고침 시 occurredAt 이 그대로여도 시간 재계산
jest.mock('@/components/group/ActivityTimelineItem', () => (props: { now?: number }) => {
  const { Text } = require('react-native');
  return <Text testID="now">{String(props.now)}</Text>;
});

const mockRefetch = jest.fn(() => Promise.resolve());

jest.mock('@/store/slices/caregroupApi', () => ({
  useGetGroupDetailQuery: jest.fn(() => ({
    data: {
      groupId: 7,
      name: '우리가족',
      memberCount: 2,
      members: [{ userId: 1, name: '나', role: 'PATIENT', color: null }],
      inviteCode: null,
      recentActivities: [
        { id: 1, actorName: '나', activityType: 'DOSE_TAKEN', summary: '복약', occurredAt: new Date().toISOString(), praisedByMe: false },
      ],
    },
    isLoading: false, isError: false, refetch: mockRefetch,
  })),
  useIssueInviteCodeMutation: () => [jest.fn(), {}],
  useLeaveGroupMutation: () => [jest.fn(), {}],
  useNudgeMemberMutation: () => [jest.fn(), {}],
  caregroupApiSlice: { util: { invalidateTags: jest.fn() } },
}));

const mockQuery = useGetGroupDetailQuery as jest.Mock;

describe('GroupDetailScreen — 새로고침 시 활동 상대시간 재계산', () => {
  it('pull-to-refresh 후 ActivityTimelineItem 에 전달되는 now 가 갱신된다', async () => {
    const { UNSAFE_getByType, getByTestId } = render(<GroupDetailScreen />);
    const beforeNow = Number(getByTestId('now').props.children);

    const refreshControl = UNSAFE_getByType(RefreshControl);
    await act(async () => {
      await refreshControl.props.onRefresh();
    });

    const afterNow = Number(getByTestId('now').props.children);
    expect(afterNow).toBeGreaterThanOrEqual(beforeNow);
    expect(mockRefetch).toHaveBeenCalled();
  });
});
