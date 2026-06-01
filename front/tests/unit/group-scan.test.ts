import { extractInviteCode } from '@/lib/inviteCode';

describe('extractInviteCode', () => {
  it('PILLMATE:JOIN:{6자리} prefix → 코드 추출', () => {
    expect(extractInviteCode('PILLMATE:JOIN:3F9K2P')).toBe('3F9K2P');
  });

  it('순수 6자리 코드 → 그대로 반환 (대문자 정규화)', () => {
    expect(extractInviteCode('3f9k2p')).toBe('3F9K2P');
  });

  it('whitespace trim 후 처리', () => {
    expect(extractInviteCode('  3F9K2P  ')).toBe('3F9K2P');
  });

  it('길이 5 → null', () => {
    expect(extractInviteCode('3F9K2')).toBeNull();
  });

  it('길이 7 → null', () => {
    expect(extractInviteCode('3F9K2PX')).toBeNull();
  });

  it('특수문자 포함 → null', () => {
    expect(extractInviteCode('3F9K-P')).toBeNull();
  });

  it('빈 문자열 → null', () => {
    expect(extractInviteCode('')).toBeNull();
  });

  it('prefix만 → null', () => {
    expect(extractInviteCode('PILLMATE:JOIN:')).toBeNull();
  });
});
