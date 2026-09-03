import React from 'react';
import { render } from '@testing-library/react-native';
import GroupScreen from '@/app/(tabs)/group/index';
import { useGetMyGroupsQuery } from '@/store/slices/caregroupApi';

jest.mock('expo-router', () => ({ router: { push: jest.fn() } }));
jest.mock('expo-haptics', () => ({ impactAsync: jest.fn(), ImpactFeedbackStyle: { Light: 'light' } }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: React.ReactNode }) => children,
}));
jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));
jest.mock('@/components/navigation/TabHeader', () => () => null);
jest.mock('@/components/group/GroupCard', () => () => null);

jest.mock('@/store/slices/caregroupApi', () => ({
  useGetMyGroupsQuery: jest.fn(() => ({ data: [], isLoading: false, isError: false })),
  usePinGroupMutation: () => [jest.fn(), {}],
  useUnpinGroupMutation: () => [jest.fn(), {}],
}));

const mockQuery = useGetMyGroupsQuery as unknown as jest.Mock;

describe('GroupScreen 자동 최신화', () => {
  it('useGetMyGroupsQuery 가 auto-refresh 옵션(mount/focus/15s polling)과 함께 호출', () => {
    render(<GroupScreen />);
    expect(mockQuery).toHaveBeenCalledWith(undefined, {
      refetchOnMountOrArgChange: true,
      refetchOnFocus: true,
      pollingInterval: 15000,
    });
  });
});
