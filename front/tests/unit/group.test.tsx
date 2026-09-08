import React from 'react';
import { render, screen } from '@testing-library/react-native';
import ActivityItem from '@/components/group/ActivityItem';
import type { GroupActivity } from '@/types/group';

const DONE_ACTIVITY: GroupActivity = {
  id: '1', who: '박순자', whoLabel: '할머니', tint: '#FF7B2E',
  time: '오늘 12:34', kind: 'done', title: '점심약 2개를 복용했어요',
  detail: ['메트포르민 500mg', '글리메피리드 2mg'],
};
const AI_ACTIVITY: GroupActivity = {
  id: '2', who: 'PillMate AI', whoLabel: 'AI', tint: '#6541F2',
  time: '오늘 09:10', kind: 'ai', title: '저녁약 미복용 패턴',
  detail: '지난 7일 중 3일 빠뜨리셨어요.', cta: '알림 조정',
};

// MemberCard 자체 테스트는 tests/unit/MemberCard.test.tsx 로 이전·최신화됨(이 블록은 중복+낡음 — 제거).

describe('ActivityItem', () => {
  it('done 활동 — 제목 렌더', () => {
    render(<ActivityItem item={DONE_ACTIVITY} />);
    expect(screen.getByText('점심약 2개를 복용했어요')).toBeTruthy();
  });

  it('done 활동 — 약 목록 렌더', () => {
    render(<ActivityItem item={DONE_ACTIVITY} />);
    expect(screen.getByText(/메트포르민 500mg/)).toBeTruthy();
  });

  it('ai 활동 — CTA 버튼 렌더', () => {
    render(<ActivityItem item={AI_ACTIVITY} />);
    expect(screen.getByText('알림 조정')).toBeTruthy();
  });

  it('시간 렌더', () => {
    render(<ActivityItem item={DONE_ACTIVITY} />);
    expect(screen.getByText('오늘 12:34')).toBeTruthy();
  });
});
