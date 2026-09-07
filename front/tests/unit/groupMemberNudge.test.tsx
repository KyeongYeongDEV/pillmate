import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
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
jest.mock('@/components/common/Avatar', () => () => null);
jest.mock('@/components/group/InviteCodeCard', () => () => null);
jest.mock('@/components/group/ActivityTimelineItem', () => () => null);
jest.mock('@/components/group/GroupScheduleCalendar', () => () => null);

jest.mock('@/lib/auth/storage', () => ({ getCurrentUserId: jest.fn() }));

const mockNudgeTrigger = jest.fn();

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
  useNudgeMemberMutation: () => [mockNudgeTrigger, {}],
  caregroupApiSlice: { util: { invalidateTags: jest.fn() } },
}));

const mockGetCurrentUserId = getCurrentUserId as jest.Mock;

function resolveWith(result: { alreadyNotified: boolean }) {
  mockNudgeTrigger.mockReturnValue({ unwrap: () => Promise.resolve(result) });
}

function rejectWith(status: number) {
  mockNudgeTrigger.mockReturnValue({ unwrap: () => Promise.reject({ status }) });
}

describe('그룹 구성원 카드 — 재촉(넛지) 벨 아이콘', () => {
  beforeEach(() => {
    mockNudgeTrigger.mockReset();
    mockGetCurrentUserId.mockReset().mockResolvedValue(1);
  });

  it('본인(userId=1) 카드에는 벨 아이콘이 렌더되지 않는다', async () => {
    render(<GroupDetailScreen />);
    await waitFor(() => expect(mockGetCurrentUserId).toHaveBeenCalled());
    expect(screen.queryByLabelText('김보호 재촉하기')).toBeNull();
  });

  it('다른 구성원 카드에는 벨 아이콘이 렌더된다', async () => {
    render(<GroupDetailScreen />);
    await waitFor(() => expect(screen.getByLabelText('박순자 재촉하기')).toBeTruthy());
  });

  it('벨 탭 → nudgeMember 가 {groupId, userId} 로 호출된다 (콘텐츠 탭과는 별개)', async () => {
    resolveWith({ alreadyNotified: false });
    render(<GroupDetailScreen />);
    await waitFor(() => screen.getByLabelText('박순자 재촉하기'));

    fireEvent.press(screen.getByLabelText('박순자 재촉하기'));

    await waitFor(() => expect(mockNudgeTrigger).toHaveBeenCalledWith({ groupId: 3, userId: 7 }));
  });

  it('200 alreadyNotified=false → "약 챙기라고 알림을 보냈어요" 토스트', async () => {
    resolveWith({ alreadyNotified: false });
    render(<GroupDetailScreen />);
    await waitFor(() => screen.getByLabelText('박순자 재촉하기'));

    fireEvent.press(screen.getByLabelText('박순자 재촉하기'));

    await waitFor(() => expect(screen.getByText('약 챙기라고 알림을 보냈어요')).toBeTruthy());
  });

  it('200 alreadyNotified=true → "이미 다른 분이 방금 알림을 보냈어요" 토스트', async () => {
    resolveWith({ alreadyNotified: true });
    render(<GroupDetailScreen />);
    await waitFor(() => screen.getByLabelText('박순자 재촉하기'));

    fireEvent.press(screen.getByLabelText('박순자 재촉하기'));

    await waitFor(() => expect(screen.getByText('이미 다른 분이 방금 알림을 보냈어요')).toBeTruthy());
  });

  it('409 → "지금은 챙길 복약이 없어요" 토스트', async () => {
    rejectWith(409);
    render(<GroupDetailScreen />);
    await waitFor(() => screen.getByLabelText('박순자 재촉하기'));

    fireEvent.press(screen.getByLabelText('박순자 재촉하기'));

    await waitFor(() => expect(screen.getByText('지금은 챙길 복약이 없어요')).toBeTruthy());
  });

  it('403 → "재촉할 수 없어요" 토스트', async () => {
    rejectWith(403);
    render(<GroupDetailScreen />);
    await waitFor(() => screen.getByLabelText('박순자 재촉하기'));

    fireEvent.press(screen.getByLabelText('박순자 재촉하기'));

    await waitFor(() => expect(screen.getByText('재촉할 수 없어요')).toBeTruthy());
  });

  it('400 → "재촉할 수 없어요" 토스트', async () => {
    rejectWith(400);
    render(<GroupDetailScreen />);
    await waitFor(() => screen.getByLabelText('박순자 재촉하기'));

    fireEvent.press(screen.getByLabelText('박순자 재촉하기'));

    await waitFor(() => expect(screen.getByText('재촉할 수 없어요')).toBeTruthy());
  });
});
