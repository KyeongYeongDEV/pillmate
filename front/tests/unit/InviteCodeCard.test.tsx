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

  it('inviteCode=null — fallback 안내 텍스트 렌더 (신규 디자인)', () => {
    render(<InviteCodeCard inviteCode={null} />);
    expect(screen.getByText('아직 발급된 초대가 없어요')).toBeTruthy();
    expect(screen.getByText('위 초대하기를 누르면 코드와 QR이 생성돼요')).toBeTruthy();
  });

  it('inviteCode=null — 빈 상태 a11y label 존재', () => {
    render(<InviteCodeCard inviteCode={null} />);
    expect(screen.getByLabelText('초대 코드 없음')).toBeTruthy();
  });

  it('inviteCode=undefined — 크래시 없음', () => {
    expect(() => render(<InviteCodeCard inviteCode={undefined} />)).not.toThrow();
  });

  it('유효한 inviteCode — QR 이미지 영역 렌더 (a11y label "초대 코드 QR")', () => {
    render(<InviteCodeCard inviteCode={VALID} />);
    expect(screen.getByLabelText('초대 코드 QR')).toBeTruthy();
  });

  it('inviteCode=null — QR 영역 미렌더', () => {
    render(<InviteCodeCard inviteCode={null} />);
    expect(screen.queryByLabelText('초대 코드 QR')).toBeNull();
  });

  describe('카운트다운 (1분 TTL)', () => {
    beforeEach(() => {
      jest.useFakeTimers();
      jest.setSystemTime(new Date('2026-06-02T00:00:00Z'));
    });
    afterEach(() => {
      jest.useRealTimers();
    });

    it('유효 inviteCode — "유효 60초" 문구 렌더', () => {
      const code = { code: '3F9K2P', expiresAt: new Date(Date.now() + 60_000).toISOString() };
      render(<InviteCodeCard inviteCode={code} />);
      expect(screen.getByText(/유효 60초/)).toBeTruthy();
    });

    it('만료 도달 → onExpire 콜백 1회 호출', () => {
      const code = { code: '3F9K2P', expiresAt: new Date(Date.now() + 60_000).toISOString() };
      const onExpire = jest.fn();
      render(<InviteCodeCard inviteCode={code} onExpire={onExpire} />);
      jest.advanceTimersByTime(60_000);
      expect(onExpire).toHaveBeenCalledTimes(1);
    });
  });
});
