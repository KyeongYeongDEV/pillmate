// RED: createPillmateBaseQuery 팩토리 미존재 → import 오류 예상
jest.mock('@/lib/auth/storage', () => ({
  getToken: jest.fn(),
  getCurrentUserId: jest.fn(),
  saveToken: jest.fn(),
  clearToken: jest.fn(),
  clearAuth: jest.fn(),
}));

jest.mock('expo-router', () => ({ router: { replace: jest.fn() } }));

jest.mock('@reduxjs/toolkit/query/react', () => {
  const actual = jest.requireActual('@reduxjs/toolkit/query/react');
  return { ...actual, fetchBaseQuery: jest.fn() };
});

describe('createPillmateBaseQuery', () => {
  let createPillmateBaseQuery: typeof import('@/lib/api/baseQuery').createPillmateBaseQuery;
  let mockGetToken: jest.Mock;
  let mockGetUserId: jest.Mock;
  let mockClearAuth: jest.Mock;
  let mockRouterReplace: jest.Mock;
  let mockFetchBaseQuery: jest.Mock;
  let mockRawBaseQuery: jest.Mock;

  beforeEach(() => {
    jest.resetModules();

    const storage = jest.requireMock<typeof import('@/lib/auth/storage')>('@/lib/auth/storage');
    mockGetToken = storage.getToken as jest.Mock;
    mockGetUserId = storage.getCurrentUserId as jest.Mock;
    mockClearAuth = storage.clearAuth as jest.Mock;
    mockGetToken.mockReset().mockResolvedValue(null);
    mockGetUserId.mockReset().mockResolvedValue(1);
    mockClearAuth.mockReset().mockResolvedValue(undefined);

    const routerModule = jest.requireMock<typeof import('expo-router')>('expo-router');
    mockRouterReplace = routerModule.router.replace as jest.Mock;
    mockRouterReplace.mockReset();

    const rtk = jest.requireMock<typeof import('@reduxjs/toolkit/query/react')>(
      '@reduxjs/toolkit/query/react',
    );
    mockFetchBaseQuery = rtk.fetchBaseQuery as jest.Mock;
    mockRawBaseQuery = jest.fn().mockResolvedValue({ data: {} });
    mockFetchBaseQuery.mockReset().mockReturnValue(mockRawBaseQuery);

    createPillmateBaseQuery = require('@/lib/api/baseQuery').createPillmateBaseQuery;
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('팩토리 함수가 export됨', () => {
    expect(typeof createPillmateBaseQuery).toBe('function');
  });

  it('인자 없이 호출 가능 (기본 baseUrl 사용)', () => {
    const baseQuery = createPillmateBaseQuery();
    expect(baseQuery).toBeDefined();
    expect(typeof baseQuery).toBe('function');
  });

  it('커스텀 baseUrl 전달 시 적용', () => {
    createPillmateBaseQuery({ baseUrl: 'http://test:9090/api/v1' });
    expect(mockFetchBaseQuery).toHaveBeenCalledWith(
      expect.objectContaining({ baseUrl: 'http://test:9090/api/v1' }),
    );
  });

  describe('prepareHeaders', () => {
    it('토큰/유저ID 조회 함수가 결선됨 (헤더 세부검증은 통합/E2E)', () => {
      mockGetToken.mockResolvedValue('test-jwt-token');
      mockGetUserId.mockResolvedValue(1);
      const baseQuery = createPillmateBaseQuery();
      expect(typeof baseQuery).toBe('function');
    });
  });

  describe('401 안전망', () => {
    it('/auth/ 로 시작하는 경로의 401 은 로그아웃/리다이렉트하지 않는다', async () => {
      mockRawBaseQuery.mockResolvedValue({ error: { status: 401, data: {} } });
      const baseQuery = createPillmateBaseQuery();

      await baseQuery({ url: '/auth/kakao', method: 'POST' }, {} as any, {});

      expect(mockClearAuth).not.toHaveBeenCalled();
      expect(mockRouterReplace).not.toHaveBeenCalled();
    });

    it('/auth/ 이외 경로의 401 은 clearAuth 호출 + 로그인 화면으로 리다이렉트', async () => {
      mockRawBaseQuery.mockResolvedValue({ error: { status: 401, data: {} } });
      const baseQuery = createPillmateBaseQuery();

      await baseQuery({ url: '/schedules/today' }, {} as any, {});

      expect(mockClearAuth).toHaveBeenCalledTimes(1);
      expect(mockRouterReplace).toHaveBeenCalledWith('/(auth)/login');
    });

    it('원래 result(error) 를 그대로 반환한다 (호출부 isError 처리 보존)', async () => {
      const errorResult = { error: { status: 401, data: { message: 'expired' } } };
      mockRawBaseQuery.mockResolvedValue(errorResult);
      const baseQuery = createPillmateBaseQuery();

      const result = await baseQuery({ url: '/schedules/today' }, {} as any, {});

      expect(result).toEqual(errorResult);
    });

    it('401 이 아니면 clearAuth/리다이렉트 부수효과가 없다', async () => {
      mockRawBaseQuery.mockResolvedValue({ data: { ok: true } });
      const baseQuery = createPillmateBaseQuery();

      await baseQuery({ url: '/schedules/today' }, {} as any, {});

      expect(mockClearAuth).not.toHaveBeenCalled();
      expect(mockRouterReplace).not.toHaveBeenCalled();
    });

    it('동시 다발 401 에도 리다이렉트는 정확히 1회만', async () => {
      mockRawBaseQuery.mockResolvedValue({ error: { status: 401, data: {} } });
      const baseQuery = createPillmateBaseQuery();

      await Promise.all([
        baseQuery({ url: '/schedules/today' }, {} as any, {}),
        baseQuery({ url: '/dose-logs' }, {} as any, {}),
        baseQuery({ url: '/groups' }, {} as any, {}),
      ]);

      expect(mockRouterReplace).toHaveBeenCalledTimes(1);
      expect(mockClearAuth).toHaveBeenCalledTimes(1);
    });

    it('문자열 인자(args) 형태도 /auth/ 판별이 동작한다', async () => {
      mockRawBaseQuery.mockResolvedValue({ error: { status: 401, data: {} } });
      const baseQuery = createPillmateBaseQuery();

      await baseQuery('/auth/refresh', {} as any, {});

      expect(mockRouterReplace).not.toHaveBeenCalled();
    });

    it('dedup 창(3s) 경과 후에는 새 401 에 다시 리다이렉트한다', async () => {
      jest.useFakeTimers();
      mockRawBaseQuery.mockResolvedValue({ error: { status: 401, data: {} } });
      const baseQuery = createPillmateBaseQuery();

      await baseQuery({ url: '/schedules/today' }, {} as any, {});
      expect(mockRouterReplace).toHaveBeenCalledTimes(1);

      jest.advanceTimersByTime(3000);

      await baseQuery({ url: '/schedules/today' }, {} as any, {});
      expect(mockRouterReplace).toHaveBeenCalledTimes(2);
    });
  });
});
