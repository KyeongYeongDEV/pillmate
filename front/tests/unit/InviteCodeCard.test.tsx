import React from 'react';
import { render, screen } from '@testing-library/react-native';
import InviteCodeCard from '@/components/group/InviteCodeCard';
import type { InviteCodeView } from '@/types/caregroup';

const VALID: InviteCodeView = {
  code: '3F9K2P',
  expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
};

describe('InviteCodeCard', () => {
  it('유효한 inviteCode — code 렌더', () => {
    render(<InviteCodeCard inviteCode={VALID} />);
    expect(screen.getByText('3F9K2P')).toBeTruthy();
  });

  it('유효한 inviteCode — 복사 버튼 렌더', () => {
    render(<InviteCodeCard inviteCode={VALID} />);
    expect(screen.getByText('복사')).toBeTruthy();
  });

  it('inviteCode=null — 크래시 없음 + fallback UI 렌더', () => {
    expect(() => render(<InviteCodeCard inviteCode={null} />)).not.toThrow();
  });

  it('inviteCode=null — fallback 안내 텍스트 렌더', () => {
    render(<InviteCodeCard inviteCode={null} />);
    expect(screen.getByText(/초대 코드가 없어요|발급되지|코드 없음/)).toBeTruthy();
  });

  it('inviteCode=undefined — 크래시 없음', () => {
    expect(() => render(<InviteCodeCard inviteCode={undefined} />)).not.toThrow();
  });
});
