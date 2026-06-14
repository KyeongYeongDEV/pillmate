import React from 'react';
import { render, screen } from '@testing-library/react-native';
import ActivityItemFull from '@/components/group/ActivityItemFull';
import type { ActivityView } from '@/types/caregroup';

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

// 상대시간 포맷은 컴포넌트가 new Date() 로 '지금'을 읽으므로 실행 날짜에 의존 → 플레이키.
// 기준 '지금'을 고정하고 fixture occurredAt 도 그 기준에 맞춘 명시적 날짜로 만들어 결정적으로 둔다.
const FIXED_NOW = new Date(2026, 5, 14, 10, 30, 0);
const TODAY_OCCURRED = new Date(2026, 5, 14, 9, 15, 0).toISOString();
const YESTERDAY_OCCURRED = new Date(2026, 5, 13, 22, 0, 0).toISOString();

const TODAY: ActivityView = {
  actorName: '박순자', activityType: 'DOSE_TAKEN',
  summary: '아침약 2개 복용', occurredAt: TODAY_OCCURRED,
};

const MISS: ActivityView = {
  actorName: '박순자', activityType: 'DOSE_MISSED',
  summary: '취침 전 약을 놓치셨어요',
  occurredAt: YESTERDAY_OCCURRED,
};

const UNKNOWN: ActivityView = {
  actorName: '아들', activityType: 'UNKNOWN_TYPE_XYZ',
  summary: '미정의 활동', occurredAt: TODAY_OCCURRED,
};

describe('ActivityItemFull', () => {
  beforeEach(() => {
    jest.useFakeTimers({ now: FIXED_NOW });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

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
    expect(screen.getByText('09:15')).toBeTruthy();
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
