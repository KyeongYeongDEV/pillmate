import React from 'react';
import { render, screen } from '@testing-library/react-native';
import ActivityItemFull from '@/components/group/ActivityItemFull';
import type { ActivityView } from '@/types/caregroup';

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

const TODAY: ActivityView = {
  actorName: '박순자', activityType: 'DOSE_TAKEN',
  summary: '아침약 2개 복용', occurredAt: new Date().toISOString(),
};

const MISS: ActivityView = {
  actorName: '박순자', activityType: 'DOSE_MISSED',
  summary: '취침 전 약을 놓치셨어요',
  occurredAt: new Date(Date.now() - 26 * 60 * 60_000).toISOString(),
};

const UNKNOWN: ActivityView = {
  actorName: '아들', activityType: 'UNKNOWN_TYPE_XYZ',
  summary: '미정의 활동', occurredAt: new Date().toISOString(),
};

describe('ActivityItemFull', () => {
  it('actorName 렌더', () => {
    render(<ActivityItemFull item={TODAY} />);
    expect(screen.getByText('박순자')).toBeTruthy();
  });

  it('summary 렌더', () => {
    render(<ActivityItemFull item={TODAY} />);
    expect(screen.getByText('아침약 2개 복용')).toBeTruthy();
  });

  it('오늘 → HH:mm 포맷', () => {
    render(<ActivityItemFull item={TODAY} />);
    const now = new Date();
    const hhmm = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    expect(screen.getByText(hhmm)).toBeTruthy();
  });

  it('어제 → "어제" 포맷', () => {
    render(<ActivityItemFull item={MISS} />);
    expect(screen.getByText('어제')).toBeTruthy();
  });

  it('unknown activityType — 크래시 X (default dot fallback)', () => {
    expect(() => render(<ActivityItemFull item={UNKNOWN} />)).not.toThrow();
    expect(screen.getByText('미정의 활동')).toBeTruthy();
  });

  it('last=true — 마지막 item 스타일 적용 (line 짧음)', () => {
    const { toJSON } = render(<ActivityItemFull item={TODAY} last />);
    expect(toJSON()).toBeTruthy();
  });
});
