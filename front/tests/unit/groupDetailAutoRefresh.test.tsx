import React from 'react';
import { render } from '@testing-library/react-native';
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
jest.mock('@/components/common/AvatarStack', () => () => null);
jest.mock('@/components/group/MemberCard', () => () => null);
jest.mock('@/components/group/InviteCodeCard', () => () => null);
jest.mock('@/components/group/ActivityTimelineItem', () => () => null);

jest.mock('@/store/slices/caregroupApi', () => ({
  useGetGroupDetailQuery: jest.fn(() => ({
    data: undefined, isLoading: true, isError: false, refetch: jest.fn(),
  })),
  useIssueInviteCodeMutation: () => [jest.fn(), {}],
  useLeaveGroupMutation: () => [jest.fn(), {}],
  caregroupApiSlice: { util: { invalidateTags: jest.fn() } },
}));

const mockQuery = useGetGroupDetailQuery as jest.Mock;

describe('GroupDetailScreen 자동 최신화', () => {
  it('useGetGroupDetailQuery 가 auto-refresh 옵션(mount/focus/7s polling)과 함께 호출', () => {
    render(<GroupDetailScreen />);
    expect(mockQuery).toHaveBeenCalledWith(7, {
      refetchOnMountOrArgChange: true,
      refetchOnFocus: true,
      pollingInterval: 7000,
    });
  });
});
