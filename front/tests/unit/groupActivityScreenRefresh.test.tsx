import React from 'react';
import { render } from '@testing-library/react-native';
import ActivityScreen from '@/app/(tabs)/group/[id]/activity';
import { useGetRecentActivityQuery } from '@/store/slices/activityApi';
import { useGetGroupDetailQuery } from '@/store/slices/caregroupApi';
import { ACTIVITY_POLL_INTERVAL_MS } from '@/lib/constants';

jest.mock('expo-router', () => ({
  useLocalSearchParams: () => ({ id: '7' }),
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: React.ReactNode }) => children,
}));
jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));
jest.mock('@/lib/auth/storage', () => ({ getCurrentUserId: () => Promise.resolve(1) }));
jest.mock('@/components/group/DaySection', () => () => null);

jest.mock('@/store/slices/activityApi', () => ({
  useGetRecentActivityQuery: jest.fn(() => ({ data: [], isLoading: false })),
}));
jest.mock('@/store/slices/caregroupApi', () => ({
  useGetGroupDetailQuery: jest.fn(() => ({ data: undefined })),
}));

const mockActivityQuery = useGetRecentActivityQuery as jest.Mock;
const mockGroupDetailQuery = useGetGroupDetailQuery as jest.Mock;

// 사용자 요청(2026-09-18) — 전체보기 화면 재진입 시 새 활동이 안 보이던 stale 캐시 버그.
// 그룹상세 화면(GROUP_DETAIL_REFRESH)과 동일하게 포커스/마운트 시 새로고침 + 폴링이 필요하다.
describe('ActivityScreen(전체보기) — 데이터 새로고침 옵션', () => {
  it('useGetRecentActivityQuery 가 refetchOnFocus/refetchOnMountOrArgChange/polling 과 함께 호출된다', () => {
    render(<ActivityScreen />);
    expect(mockActivityQuery).toHaveBeenCalledWith(
      { groupId: 7 },
      expect.objectContaining({
        refetchOnFocus: true,
        refetchOnMountOrArgChange: true,
        pollingInterval: ACTIVITY_POLL_INTERVAL_MS,
      }),
    );
  });

  it('useGetGroupDetailQuery 도 refetchOnFocus/refetchOnMountOrArgChange 와 함께 호출된다', () => {
    render(<ActivityScreen />);
    expect(mockGroupDetailQuery).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ refetchOnFocus: true, refetchOnMountOrArgChange: true }),
    );
  });
});
