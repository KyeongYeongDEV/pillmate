jest.mock('@/lib/auth/devUserId', () => ({ resolveDevUserId: jest.fn() }));

import { devUserIdHeaders } from '@/store/slices/authApi';
import { resolveDevUserId } from '@/lib/auth/devUserId';

const mockResolve = resolveDevUserId as jest.Mock;

describe('devUserIdHeaders — dev userId 헤더 빌드', () => {
  afterEach(() => mockResolve.mockReset());

  it('resolveDevUserId 값 있으면 X-Dev-User-Id 헤더 반환', () => {
    mockResolve.mockReturnValue('2');
    expect(devUserIdHeaders()).toEqual({ 'X-Dev-User-Id': '2' });
  });

  it('resolveDevUserId null 이면 빈 객체 (헤더 미주입)', () => {
    mockResolve.mockReturnValue(null);
    expect(devUserIdHeaders()).toEqual({});
  });
});
