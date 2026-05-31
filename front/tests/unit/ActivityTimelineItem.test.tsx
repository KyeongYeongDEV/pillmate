import React from 'react';
import { render, screen } from '@testing-library/react-native';
import ActivityTimelineItem from '@/components/group/ActivityTimelineItem';
import type { ActivityView } from '@/types/caregroup';

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
    expect(screen.getByText('박순자')).toBeTruthy();
  });

  it('summary 렌더', () => {
    render(<ActivityTimelineItem item={DONE} />);
    expect(screen.getByText('아침약 2개 복용')).toBeTruthy();
  });

  it('DOSE_TAKEN — "복용" chip 렌더', () => {
    render(<ActivityTimelineItem item={DONE} />);
    expect(screen.getByText('복용')).toBeTruthy();
  });

  it('DOSE_MISSED — "미복용" chip 렌더', () => {
    render(<ActivityTimelineItem item={MISSED} />);
    expect(screen.getByText('미복용')).toBeTruthy();
  });

  it('5분 전 포맷', () => {
    render(<ActivityTimelineItem item={DONE} />);
    expect(screen.getByText('5분 전')).toBeTruthy();
  });

  it('1시간 전 포맷', () => {
    render(<ActivityTimelineItem item={MISSED} />);
    expect(screen.getByText('1시간 전')).toBeTruthy();
  });
});
