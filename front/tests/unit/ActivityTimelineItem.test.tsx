import React from 'react';
import { render, screen, act } from '@testing-library/react-native';
import ActivityTimelineItem from '@/components/group/ActivityTimelineItem';
import type { ActivityView } from '@/types/caregroup';

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));
jest.mock('@/components/group/PraiseButton', () => (({ activityFeedId, groupId }: { activityFeedId: number; groupId: number }) => {
  const { Text } = require('react-native');
  return <Text>칭찬버튼:{activityFeedId}:{groupId}</Text>;
}) as React.FC<{ activityFeedId: number; groupId: number }>);

const DONE: ActivityView = {
  id: 1,
  actorName: '박순자',
  activityType: 'DOSE_TAKEN',
  summary: '아침약 2개 복용',
  occurredAt: new Date(Date.now() - 5 * 60_000).toISOString(),
};

const MISSED: ActivityView = {
  id: 2,
  actorName: '박순자',
  activityType: 'DOSE_MISSED',
  summary: '저녁약을 놓치셨어요',
  occurredAt: new Date(Date.now() - 60 * 60_000).toISOString(),
};

describe('ActivityTimelineItem', () => {
  it('actorName 렌더', () => {
    render(<ActivityTimelineItem item={DONE} />);
    expect(screen.getByText(/박순자/)).toBeTruthy();
  });

  it('summary 렌더 (title)', () => {
    render(<ActivityTimelineItem item={DONE} />);
    expect(screen.getByText('아침약 2개 복용')).toBeTruthy();
  });

  it('DOSE_MISSED summary 렌더', () => {
    render(<ActivityTimelineItem item={MISSED} />);
    expect(screen.getByText('저녁약을 놓치셨어요')).toBeTruthy();
  });

  it('5분 전 포맷', () => {
    render(<ActivityTimelineItem item={DONE} />);
    expect(screen.getByText('5분 전')).toBeTruthy();
  });

  it('1시간 전 포맷', () => {
    render(<ActivityTimelineItem item={MISSED} />);
    expect(screen.getByText('1시간 전')).toBeTruthy();
  });

  it('whoLabel prop — 별칭 렌더 (예: "· 할머니")', () => {
    render(<ActivityTimelineItem item={DONE} whoLabel="할머니" />);
    expect(screen.getByText(/할머니/)).toBeTruthy();
  });

  it('tint prop 을 주면 그 색이 Avatar 에 쓰인다', () => {
    render(<ActivityTimelineItem item={DONE} tint="#123456" />);
    expect(screen.getByText('박').parent?.parent?.props.style.backgroundColor).toBe('#123456');
  });

  it('DOSE_TAKEN + groupId 있으면 칭찬 버튼 렌더', () => {
    render(<ActivityTimelineItem item={DONE} groupId={20} />);
    expect(screen.getByText('칭찬버튼:1:20')).toBeTruthy();
  });

  it('groupId 없으면 칭찬 버튼 미렌더', () => {
    render(<ActivityTimelineItem item={DONE} />);
    expect(screen.queryByText(/칭찬버튼/)).toBeNull();
  });

  it('본인 활동(excludeActorName 일치)이면 칭찬 버튼 미렌더', () => {
    render(<ActivityTimelineItem item={DONE} groupId={20} excludeActorName="박순자" />);
    expect(screen.queryByText(/칭찬버튼/)).toBeNull();
  });

  // 사용자 요청(2026-09-17): 그룹 상세 새로고침 시 occurredAt 이 그대로여도(React.memo 로 재렌더 안 됨)
  // "N분 전" 표시가 최신 시각 기준으로 재계산돼야 한다 — now prop 을 명시적으로 흘려 memo 를 우회.
  it('now prop 이 바뀌면 occurredAt 이 그대로여도 상대시간이 재계산된다(새로고침 시나리오)', () => {
    const baseNow = Date.now();
    const { rerender } = render(<ActivityTimelineItem item={DONE} now={baseNow} />);
    expect(screen.getByText('5분 전')).toBeTruthy();

    rerender(<ActivityTimelineItem item={DONE} now={baseNow + 10 * 60_000} />);

    expect(screen.getByText('15분 전')).toBeTruthy();
    expect(screen.queryByText('5분 전')).toBeNull();
  });
});
