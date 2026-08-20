import { refreshSessionIfNeeded, remainingMs } from '@/lib/auth/refreshSession';
import { getToken, saveToken } from '@/lib/auth/storage';

jest.mock('@/lib/auth/storage', () => ({
  getToken: jest.fn(),
  saveToken: jest.fn(),
}));

const mockGetToken = getToken as jest.Mock;
const mockSaveToken = saveToken as jest.Mock;

const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;

function base64UrlEncode(payload: unknown): string {
  const base64 = Buffer.from(JSON.stringify(payload)).toString('base64');
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function buildToken(payload: unknown): string {
  const header = base64UrlEncode({ alg: 'HS256', typ: 'JWT' });
  const body = base64UrlEncode(payload);
  return `${header}.${body}.signature`;
}

describe('remainingMs', () => {
  it('미래 exp 는 양수를 반환한다', () => {
    const now = 1_700_000_000_000;
    const token = buildToken({ exp: Math.floor(now / 1000) + 3600 });
    expect(remainingMs(token, now)).toBe(3600 * 1000);
  });

  it('과거 exp 는 0 이하(음수)를 반환한다', () => {
    const now = 1_700_000_000_000;
    const token = buildToken({ exp: Math.floor(now / 1000) - 60 });
    expect(remainingMs(token, now)).toBeLessThanOrEqual(0);
    expect(remainingMs(token, now)).toBe(-60 * 1000);
  });

  it('exp 클레임이 없으면 0을 반환한다', () => {
    const token = buildToken({ sub: 'user-1' });
    expect(remainingMs(token, Date.now())).toBe(0);
  });

  it('JWT 형식이 아닌 토큰은 0을 반환한다 (파싱 실패)', () => {
    expect(remainingMs('not-a-jwt-token', Date.now())).toBe(0);
  });

  it('payload 가 JSON 이 아니면 0을 반환한다', () => {
    expect(remainingMs('a.b.c', Date.now())).toBe(0);
  });
});

describe('refreshSessionIfNeeded', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    mockGetToken.mockReset();
    mockSaveToken.mockReset();
    global.fetch = jest.fn();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('저장된 토큰이 없으면 fetch 를 호출하지 않는다', async () => {
    mockGetToken.mockResolvedValue(null);

    await refreshSessionIfNeeded();

    expect(global.fetch).not.toHaveBeenCalled();
    expect(mockSaveToken).not.toHaveBeenCalled();
  });

  it('남은 유효기간이 7일 이상이면 fetch 를 호출하지 않는다', async () => {
    const now = Date.now();
    const token = buildToken({ exp: Math.floor((now + SEVEN_DAYS_MS + 1000) / 1000) });
    mockGetToken.mockResolvedValue(token);

    await refreshSessionIfNeeded();

    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('남은 유효기간이 7일 미만이면 /auth/refresh 를 호출하고 성공 시 saveToken 한다', async () => {
    const now = Date.now();
    const token = buildToken({ exp: Math.floor((now + 60_000) / 1000) });
    mockGetToken.mockResolvedValue(token);
    (global.fetch as jest.Mock).mockResolvedValue({
      ok: true,
      json: async () => ({ data: { token: 'new-token-abc' } }),
    });

    await refreshSessionIfNeeded();

    expect(global.fetch).toHaveBeenCalledTimes(1);
    const [url, options] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain('/auth/refresh');
    expect(options.method).toBe('POST');
    expect(options.headers.Authorization).toBe(`Bearer ${token}`);
    expect(mockSaveToken).toHaveBeenCalledWith('new-token-abc');
  });

  it('이미 만료된 토큰(음수 remainingMs)도 갱신을 시도한다', async () => {
    const now = Date.now();
    const token = buildToken({ exp: Math.floor((now - 60_000) / 1000) });
    mockGetToken.mockResolvedValue(token);
    (global.fetch as jest.Mock).mockResolvedValue({
      ok: true,
      json: async () => ({ data: { token: 'refreshed-token' } }),
    });

    await refreshSessionIfNeeded();

    expect(global.fetch).toHaveBeenCalledTimes(1);
    expect(mockSaveToken).toHaveBeenCalledWith('refreshed-token');
  });

  it('서버가 401 을 반환해도 saveToken 을 호출하지 않고 조용히 종료한다', async () => {
    const now = Date.now();
    const token = buildToken({ exp: Math.floor((now + 60_000) / 1000) });
    mockGetToken.mockResolvedValue(token);
    (global.fetch as jest.Mock).mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({}),
    });

    await expect(refreshSessionIfNeeded()).resolves.toBeUndefined();
    expect(mockSaveToken).not.toHaveBeenCalled();
  });

  it('fetch 자체가 실패(네트워크 에러)해도 throw 하지 않는다', async () => {
    const now = Date.now();
    const token = buildToken({ exp: Math.floor((now + 60_000) / 1000) });
    mockGetToken.mockResolvedValue(token);
    (global.fetch as jest.Mock).mockRejectedValue(new Error('network down'));

    await expect(refreshSessionIfNeeded()).resolves.toBeUndefined();
    expect(mockSaveToken).not.toHaveBeenCalled();
  });

  it('data.token 이 없는 응답이어도 throw 없이 조용히 종료한다', async () => {
    const now = Date.now();
    const token = buildToken({ exp: Math.floor((now + 60_000) / 1000) });
    mockGetToken.mockResolvedValue(token);
    (global.fetch as jest.Mock).mockResolvedValue({
      ok: true,
      json: async () => ({ data: null }),
    });

    await expect(refreshSessionIfNeeded()).resolves.toBeUndefined();
    expect(mockSaveToken).not.toHaveBeenCalled();
  });
});
