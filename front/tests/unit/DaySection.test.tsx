import React from 'react';
import { render, screen } from '@testing-library/react-native';
import DaySection from '@/components/group/DaySection';
import type { ActivityView } from '@/types/caregroup';

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

const ITEMS: ActivityView[] = [
  { actorName: '박순자', activityType: 'DOSE_TAKEN', summary: '아침약 복용', occurredAt: new Date().toISOString() },
  { actorName: '김민지', activityType: 'PRESCRIPTION_ADDED', summary: '약봉투 등록', occurredAt: new Date().toISOString() },
];

describe('DaySection', () => {
  it('title 렌더', () => {
    render(<DaySection title="오늘 · 6월 1일 월" items={ITEMS} first />);
    expect(screen.getByText('오늘 · 6월 1일 월')).toBeTruthy();
  });

  it('모든 item summary 렌더', () => {
    render(<DaySection title="오늘" items={ITEMS} />);
    expect(screen.getByText('아침약 복용')).toBeTruthy();
    expect(screen.getByText('약봉투 등록')).toBeTruthy();
  });

  it('빈 items → null 렌더 (아무것도 표시 안함)', () => {
    const { toJSON } = render(<DaySection title="이전 활동" items={[]} />);
    expect(toJSON()).toBeNull();
  });

  it('item count 만큼 렌더 (3개)', () => {
    const three = [...ITEMS, { actorName: '아들', activityType: 'COMMENT', summary: '메모 추가', occurredAt: new Date().toISOString() }];
    render(<DaySection title="오늘" items={three} />);
    expect(screen.getByText('메모 추가')).toBeTruthy();
  });
});
