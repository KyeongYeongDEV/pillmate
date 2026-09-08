import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';
import GroupDetailScreen from '@/app/(tabs)/group/[id]';
import { getCurrentUserId } from '@/lib/auth/storage';

jest.mock('expo-router', () => ({
  useLocalSearchParams: () => ({ id: '3' }),
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
jest.mock('@/components/common/AvatarStack', () => () => null);
jest.mock('@/components/group/InviteCodeCard', () => () => null);
jest.mock('@/components/group/ActivityTimelineItem', () => () => null);
jest.mock('@/components/group/GroupScheduleCalendar', () => () => null);

jest.mock('@/lib/auth/storage', () => ({ getCurrentUserId: jest.fn() }));

jest.mock('@/store/slices/caregroupApi', () => ({
  useGetGroupDetailQuery: () => ({
    data: {
      groupId: 3,
      name: '할머니 댁',
      memberCount: 2,
      members: [
        { userId: 7, name: '박순자', role: 'PATIENT' },
        { userId: 1, name: '김보호', role: 'GUARDIAN' },
      ],
      inviteCode: null,
      recentActivities: [],
    },
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
  }),
  useIssueInviteCodeMutation: () => [jest.fn(), {}],
  useLeaveGroupMutation: () => [jest.fn(), {}],
  useNudgeMemberMutation: () => [jest.fn(), {}],
  caregroupApiSlice: { util: { invalidateTags: jest.fn() } },
}));

const mockGetCurrentUserId = getCurrentUserId as jest.Mock;
const mockPush = router.push as jest.Mock;

describe('그룹 구성원 탭 → 복약 캘린더 이동', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockGetCurrentUserId.mockReset().mockResolvedValue(1);
  });

  it('다른 구성원을 탭하면 그 사람의 복약 화면으로 이동한다', async () => {
    render(<GroupDetailScreen />);

    fireEvent.press(screen.getByLabelText('박순자 환자'));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith({
      pathname: '/group/[id]/member/[userId]',
      params: { id: '3', userId: '7', name: '박순자' },
    }));
  });

  it('본인을 탭하면 체크 가능한 내 복약 탭으로 이동한다', async () => {
    render(<GroupDetailScreen />);

    fireEvent.press(screen.getByLabelText('김보호 보호자'));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/(tabs)/schedule'));
  });

  it('헤더의 그룹 설정 아이콘을 탭하면 설정 화면으로 이동한다', () => {
    render(<GroupDetailScreen />);

    fireEvent.press(screen.getByLabelText('그룹 설정'));

    expect(mockPush).toHaveBeenCalledWith('/group/3/settings');
  });
});
