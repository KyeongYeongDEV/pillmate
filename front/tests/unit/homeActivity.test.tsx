import React from 'react';
import { render, screen } from '@testing-library/react-native';
import ActivityFeedItem from '@/components/home/ActivityFeedItem';
import type { ActivityFeedItem as ActivityFeedItemType } from '@/types/activity';

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

const BASE: ActivityFeedItemType = {
  id: 1,
  actorNickname: '할머니',
  activityType: 'DOSE_TAKEN',
  timeSlot: 'MORNING',
  summary: '아침약 2개를 복용했어요',
  severity: 'INFO',
  occurredAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
};

describe('ActivityFeedItem', () => {
  it('actorNickname + summary 렌더', () => {
    render(<ActivityFeedItem item={BASE} />);
    expect(screen.getByText('할머니')).toBeTruthy();
    expect(screen.getByText(/아침약 2개를 복용했어요/)).toBeTruthy();
  });

  it('timeSlot 슬롯 라벨 포함', () => {
    render(<ActivityFeedItem item={BASE} />);
    expect(screen.getByText(/아침/)).toBeTruthy();
  });

  it('INFO severity → 뱃지 미노출', () => {
    render(<ActivityFeedItem item={BASE} />);
    expect(screen.queryByText('⚠ 주의')).toBeNull();
  });

  it('actorUserId 필드 없음 (PII 제거)', () => {
    expect(BASE).not.toHaveProperty('actorUserId');
  });

  it('방금 전 포맷', () => {
    render(<ActivityFeedItem item={{ ...BASE, occurredAt: new Date().toISOString() }} />);
    expect(screen.getByText('방금')).toBeTruthy();
  });

  it('분 전 포맷', () => {
    render(<ActivityFeedItem item={{ ...BASE, occurredAt: new Date(Date.now() - 3 * 60 * 1000).toISOString() }} />);
    expect(screen.getByText('3분 전')).toBeTruthy();
  });

  // 사용자 요청(2026-09-15): 홈 고정 그룹 알림에도 구성원 개개인 색상 적용
  it('tint 전달 시 severity 색 대신 구성원 고유색으로 아바타 렌더', () => {
    render(<ActivityFeedItem item={BASE} tint="#7E57C2" />);
    const avatarView = screen.getByText('할').parent?.parent;
    const flatStyle = Object.assign({}, ...(avatarView!.props.style as object[]));
    expect(flatStyle.backgroundColor).toBe('#7E57C2');
  });

  it('tint 미전달 시 기존 severity 기반 색 유지(회귀 없음)', () => {
    render(<ActivityFeedItem item={BASE} />);
    const avatarView = screen.getByText('할').parent?.parent;
    const flatStyle = Object.assign({}, ...(avatarView!.props.style as object[]));
    expect(flatStyle.backgroundColor).not.toBe('#7E57C2');
  });
});
