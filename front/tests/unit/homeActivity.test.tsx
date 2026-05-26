import React from 'react';
import { render, screen } from '@testing-library/react-native';
import ActivityFeedItem from '@/components/home/ActivityFeedItem';
import type { ActivityFeedItem as ActivityFeedItemType } from '@/types/activity';

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

const BASE: ActivityFeedItemType = {
  id: 1,
  actorUserId: 2,
  actorName: '박순자',
  activityType: 'DOSE_TAKEN',
  summary: '아침약 2개를 복용했어요',
  severity: 'INFO',
  occurredAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
};

// ── ActivityFeedItem 렌더 ──────────────────────────────────────────────

describe('ActivityFeedItem', () => {
  it('actorName + summary 렌더', () => {
    render(<ActivityFeedItem item={BASE} />);
    expect(screen.getByText('박순자')).toBeTruthy();
    expect(screen.getByText(/아침약 2개를 복용했어요/)).toBeTruthy();
  });

  it('INFO severity → 뱃지 미노출', () => {
    render(<ActivityFeedItem item={BASE} />);
    expect(screen.queryByText('⚠ 주의')).toBeNull();
    expect(screen.queryByText('⚠ 위험')).toBeNull();
  });

  it('WARN severity → 주의 뱃지 표시', () => {
    render(<ActivityFeedItem item={{ ...BASE, severity: 'WARN', activityType: 'DOSE_MISSED', summary: '취침 전 약을 놓치셨어요' }} />);
    expect(screen.getByText('⚠ 주의')).toBeTruthy();
  });

  it('CRITICAL severity → 위험 뱃지 표시', () => {
    render(<ActivityFeedItem item={{ ...BASE, severity: 'CRITICAL', summary: '혈압약 3일 연속 미복용' }} />);
    expect(screen.getByText('⚠ 위험')).toBeTruthy();
  });

  it('방금 전 포맷', () => {
    const now = new Date().toISOString();
    render(<ActivityFeedItem item={{ ...BASE, occurredAt: now }} />);
    expect(screen.getByText('방금')).toBeTruthy();
  });

  it('분 전 포맷', () => {
    const past = new Date(Date.now() - 3 * 60 * 1000).toISOString();
    render(<ActivityFeedItem item={{ ...BASE, occurredAt: past }} />);
    expect(screen.getByText('3분 전')).toBeTruthy();
  });
});
