import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import GroupCard from '@/components/group/GroupCard';
import type { MyGroupSummary } from '@/types/caregroup';

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));
jest.mock('@/components/common/AvatarStack', () => () => null);

const GROUP: MyGroupSummary = {
  groupId: 1, name: '할머니 댁', role: '보호자',
  memberCount: 3, membersPreview: ['박', '김', '이'],
  lastActivity: { summary: '아침약 복용', activityType: 'DOSE_TAKEN', severity: 'INFO', occurredAt: new Date(Date.now() - 5 * 60_000).toISOString() },
  unreadCount: 0, pinned: false,
};

describe('GroupCard', () => {
  it('그룹명 렌더', () => {
    render(<GroupCard group={GROUP} onPress={jest.fn()} onPinToggle={jest.fn()} />);
    expect(screen.getByText('할머니 댁')).toBeTruthy();
  });

  it('멤버수 + 역할 렌더', () => {
    render(<GroupCard group={GROUP} onPress={jest.fn()} onPinToggle={jest.fn()} />);
    expect(screen.getByText(/3명/)).toBeTruthy();
  });

  it('lastActivity summary 렌더', () => {
    render(<GroupCard group={GROUP} onPress={jest.fn()} onPinToggle={jest.fn()} />);
    expect(screen.getByText('아침약 복용')).toBeTruthy();
  });

  it('onPress 호출', () => {
    const onPress = jest.fn();
    render(<GroupCard group={GROUP} onPress={onPress} onPinToggle={jest.fn()} />);
    fireEvent.press(screen.getByText('할머니 댁'));
    expect(onPress).toHaveBeenCalledWith(1);
  });

  it('isPinned — 고정 스타일 적용 (pinnedCard 렌더)', () => {
    const { toJSON } = render(<GroupCard group={{ ...GROUP, pinned: true }} onPress={jest.fn()} onPinToggle={jest.fn()} isPinned />);
    expect(toJSON()).toBeTruthy();
  });

  it('lastActivity null — summary 미렌더', () => {
    render(<GroupCard group={{ ...GROUP, lastActivity: null }} onPress={jest.fn()} onPinToggle={jest.fn()} />);
    expect(screen.queryByText('아침약 복용')).toBeNull();
  });
});
