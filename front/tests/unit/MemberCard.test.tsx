import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import MemberCard from '@/components/group/MemberCard';
import type { GroupMember } from '@/types/group';

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));
jest.mock('@/components/common/Avatar', () => () => null);

const MEMBER: GroupMember = {
  id: '7', name: '박순자', sub: '환자', role: '환자', tint: '#000', online: false,
};

describe('MemberCard — 재촉(넛지) 벨 아이콘', () => {
  it('onNudge 를 넘기면 벨 아이콘 영역이 렌더된다', () => {
    render(<MemberCard member={MEMBER} onPress={jest.fn()} onNudge={jest.fn()} />);
    expect(screen.getByLabelText('박순자 재촉하기')).toBeTruthy();
  });

  it('onNudge 를 넘기지 않으면(본인 카드) 벨 아이콘이 렌더되지 않는다', () => {
    render(<MemberCard member={MEMBER} onPress={jest.fn()} />);
    expect(screen.queryByLabelText('박순자 재촉하기')).toBeNull();
  });

  it('콘텐츠 영역 탭 → onPress 만 호출, onNudge 는 호출되지 않는다', () => {
    const onPress = jest.fn();
    const onNudge = jest.fn();
    render(<MemberCard member={MEMBER} onPress={onPress} onNudge={onNudge} />);
    fireEvent.press(screen.getByLabelText('박순자 환자'));
    expect(onPress).toHaveBeenCalledWith(MEMBER);
    expect(onNudge).not.toHaveBeenCalled();
  });

  it('벨 아이콘 탭 → onNudge 만 호출, onPress 는 호출되지 않는다', () => {
    const onPress = jest.fn();
    const onNudge = jest.fn();
    render(<MemberCard member={MEMBER} onPress={onPress} onNudge={onNudge} />);
    fireEvent.press(screen.getByLabelText('박순자 재촉하기'));
    expect(onNudge).toHaveBeenCalledWith(MEMBER);
    expect(onPress).not.toHaveBeenCalled();
  });

  it('nudging=true 이면 벨 버튼이 disabled 상태다', () => {
    const onNudge = jest.fn();
    render(<MemberCard member={MEMBER} onPress={jest.fn()} onNudge={onNudge} nudging />);
    const button = screen.getByLabelText('박순자 재촉하기');
    fireEvent.press(button);
    expect(onNudge).not.toHaveBeenCalled();
  });
});

describe('MemberCard — 본인 행의 알약 정보 공유 설정 아이콘', () => {
  const ME: GroupMember = { ...MEMBER, isMe: true };

  it('본인 행이면 벨 대신 설정 아이콘이 렌더된다', () => {
    render(<MemberCard member={ME} onPress={jest.fn()} onSettingsPress={jest.fn()} />);
    expect(screen.getByLabelText('알약 정보 공유 설정')).toBeTruthy();
    expect(screen.queryByLabelText('박순자 재촉하기')).toBeNull();
  });

  it('설정 아이콘 탭 → onSettingsPress 호출', () => {
    const onSettingsPress = jest.fn();
    render(<MemberCard member={ME} onPress={jest.fn()} onSettingsPress={onSettingsPress} />);
    fireEvent.press(screen.getByLabelText('알약 정보 공유 설정'));
    expect(onSettingsPress).toHaveBeenCalledTimes(1);
  });
});
