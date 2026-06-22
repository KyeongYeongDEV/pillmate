import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import FamilyActivityFeed from '@/components/home/FamilyActivityFeed';
import type { ActivityFeedItem } from '@/types/activity';

jest.mock('expo-router', () => ({ router: { push: jest.fn() } }));
jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));
jest.mock('@/components/home/ActivityFeedItem', () => (({ item }: { item: ActivityFeedItem }) => {
  const { Text } = require('react-native');
  return <Text>{item.summary}</Text>;
}) as React.FC<{ item: ActivityFeedItem }>);

const FEED: ActivityFeedItem[] = [{
  actorNickname: '할머니',
  activityType: 'DOSE_TAKEN',
  timeSlot: 'MORNING',
  summary: '아침약 2개를 복용했어요',
  severity: 'INFO',
  occurredAt: new Date(Date.now() - 5 * 60_000).toISOString(),
}];

describe('FamilyActivityFeed', () => {
  it('고정 그룹 없을 때 안내 문구 렌더', () => {
    render(<FamilyActivityFeed feed={[]} isLoading={false} isError={false} hasPinnedGroup={false} />);
    expect(screen.getByText(/그룹을 고정하면/)).toBeTruthy();
  });

  it('고정 그룹 없을 때 CTA 버튼 렌더', () => {
    render(<FamilyActivityFeed feed={[]} isLoading={false} isError={false} hasPinnedGroup={false} />);
    expect(screen.getByText(/그룹 고정하러 가기/)).toBeTruthy();
  });

  it('고정 그룹 없을 때 CTA 누르면 그룹 탭 이동', () => {
    const { router } = require('expo-router');
    (router.push as jest.Mock).mockClear();
    render(<FamilyActivityFeed feed={[]} isLoading={false} isError={false} hasPinnedGroup={false} />);
    fireEvent.press(screen.getByText(/그룹 고정하러 가기/));
    expect(router.push).toHaveBeenCalledWith('/(tabs)/group');
  });

  it('고정 그룹 있고 로딩 중 → 로딩 표시', () => {
    render(<FamilyActivityFeed feed={[]} isLoading={true} isError={false} hasPinnedGroup={true} />);
    expect(screen.getByTestId('activity-loading')).toBeTruthy();
  });

  it('고정 그룹 있고 피드 있음 → 피드 렌더', () => {
    render(<FamilyActivityFeed feed={FEED} isLoading={false} isError={false} hasPinnedGroup={true} />);
    expect(screen.getByTestId('activity-data')).toBeTruthy();
    expect(screen.getByText('아침약 2개를 복용했어요')).toBeTruthy();
  });

  it('고정 그룹 있고 피드 비어있음 → 가족 활동 없음 문구', () => {
    render(<FamilyActivityFeed feed={[]} isLoading={false} isError={false} hasPinnedGroup={true} />);
    expect(screen.getByText(/아직 가족 활동이 없어요/)).toBeTruthy();
  });
});
