import React from 'react';
import { render, screen } from '@testing-library/react-native';
import ActivityTimelineItem from '@/components/group/ActivityTimelineItem';
import type { ActivityView } from '@/types/caregroup';

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

const DONE: ActivityView = {
  actorName: '박순자',
  activityType: 'DOSE_TAKEN',
  summary: '아침약 2개 복용',
  occurredAt: new Date(Date.now() - 5 * 60_000).toISOString(),
};

const MISSED: ActivityView = {
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
});
