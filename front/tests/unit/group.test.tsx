import React from 'react';
import { render, screen } from '@testing-library/react-native';
import MemberCard from '@/components/group/MemberCard';
import ActivityItem from '@/components/group/ActivityItem';
import type { GroupMember, GroupActivity } from '@/types/group';

const PATIENT: GroupMember = {
  id: '1', name: '박순자', sub: '환자 · 만 72세', role: '환자',
  tint: '#FF7B2E', online: true,
};
const GUARDIAN: GroupMember = {
  id: '2', name: '김민지', sub: '딸 · 본인', role: '보호자',
  tint: '#0066FF', online: true, isMe: true,
};

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

describe('MemberCard', () => {
  it('이름 + 서브텍스트 렌더', () => {
    render(<MemberCard member={PATIENT} isFirst />);
    expect(screen.getByText('박순자')).toBeTruthy();
    expect(screen.getByText('환자 · 만 72세')).toBeTruthy();
  });

  it('환자 역할 뱃지 렌더', () => {
    render(<MemberCard member={PATIENT} isFirst />);
    expect(screen.getByText('환자')).toBeTruthy();
  });

  it('나 뱃지 렌더 (isMe)', () => {
    render(<MemberCard member={GUARDIAN} isFirst />);
    expect(screen.getByText('나')).toBeTruthy();
  });

  it('보호자 역할 렌더', () => {
    render(<MemberCard member={GUARDIAN} isFirst />);
    expect(screen.getByText('보호자')).toBeTruthy();
  });
});

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
