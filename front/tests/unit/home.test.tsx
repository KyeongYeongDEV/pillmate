import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import HomeScreen from '@/app/(tabs)/home';
import { router } from 'expo-router';
import { useGetMyGroupsQuery } from '@/store/slices/caregroupApi';

jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: React.ReactNode }) => children,
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));

jest.mock('expo-router', () => ({ router: { push: jest.fn() } }));

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

jest.mock('@/store/hooks', () => ({ useAppSelector: () => ({}) }));

jest.mock('@/store/slices/activityApi', () => ({
  useGetRecentActivityQuery: () => ({ data: [], isLoading: false, isError: false }),
}));

jest.mock('@/store/slices/caregroupApi', () => ({ useGetMyGroupsQuery: jest.fn() }));

jest.mock('@/store/slices/prescriptionApi', () => ({
  useGetLatestWithInsightQuery: () => ({ data: null }),
}));

jest.mock('@/store/slices/scheduleApi', () => ({
  useGetDayScheduleQuery: () => ({ data: { slots: [] } }),
}));

jest.mock('@/hooks/useSlotPress', () => ({ useSlotPress: () => jest.fn() }));
jest.mock('@/hooks/useDoseStreak', () => ({ useDoseStreak: () => 0 }));

jest.mock('@/components/home/TimeSlotCards', () => () => null);
jest.mock('@/components/home/InsightCard', () => () => null);
jest.mock('@/components/home/FamilyActivityFeed', () => () => null);
jest.mock('@/components/home/DoseStatusRow', () => () => null);
jest.mock('@/components/home/NotificationBell', () => () => null);

const mockGroups = useGetMyGroupsQuery as jest.Mock;

describe('HomeScreen 고정 그룹 알림 헤더 링크', () => {
  beforeEach(() => (router.push as jest.Mock).mockClear());

  it('핀 그룹 있으면 "알림 보러가기" 노출 + 그룹 activity 라우팅', () => {
    mockGroups.mockReturnValue({ data: [{ groupId: 7, pinned: true }] });
    render(<HomeScreen />);

    const link = screen.getByLabelText('알림 보러가기');
    expect(link).toBeTruthy();

    fireEvent.press(link);
    expect(router.push).toHaveBeenCalledWith({
      pathname: '/group/[id]/activity',
      params: { id: '7' },
    });
  });

  it('핀 그룹 없으면 "알림 보러가기" 미노출', () => {
    mockGroups.mockReturnValue({ data: [{ groupId: 7, pinned: false }] });
    render(<HomeScreen />);
    expect(screen.queryByLabelText('알림 보러가기')).toBeNull();
  });
});
