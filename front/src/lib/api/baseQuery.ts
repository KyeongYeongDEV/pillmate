import { fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { router } from 'expo-router';
import { API_BASE_URL } from './client';
import { getToken, getCurrentUserId, clearAuth } from '@/lib/auth/storage';

interface BaseQueryConfig {
  baseUrl?: string;
}

type RawBaseQuery = ReturnType<typeof fetchBaseQuery>;

const AUTH_PATH_PREFIX = '/auth/';
// 로그인 실패 직후 잇따르는 401 을 같은 사건으로 취급해 리다이렉트를 1회로 묶는 창.
const REDIRECT_DEDUP_WINDOW_MS = 3000;

// 여러 요청이 동시에 401 을 받아도 로그인 화면 리다이렉트는 한 번만 발생시키는 dedup 플래그.
let isRedirectingToLogin = false;

function isAuthEndpoint(args: Parameters<RawBaseQuery>[0]): boolean {
  const url = typeof args === 'string' ? args : args.url;
  return url.startsWith(AUTH_PATH_PREFIX);
}

function redirectToLoginOnce(): void {
  if (isRedirectingToLogin) return;
  isRedirectingToLogin = true;
  void clearAuth();
  router.replace('/(auth)/login');
  const dedupTimer = setTimeout(() => {
    isRedirectingToLogin = false;
  }, REDIRECT_DEDUP_WINDOW_MS) as unknown as { unref?: () => void };
  dedupTimer.unref?.(); // Node(Jest) 프로세스 종료 지연 방지 — RN 런타임엔 unref 없어도 안전
}

export const createPillmateBaseQuery = (config?: BaseQueryConfig): RawBaseQuery => {
  const rawBaseQuery: RawBaseQuery = fetchBaseQuery({
    baseUrl: config?.baseUrl ?? API_BASE_URL,
    prepareHeaders: async (headers) => {
      const token = await getToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      const userId = await getCurrentUserId();
      if (userId != null) headers.set('X-User-Id', String(userId));
      return headers;
    },
  });

  return async (args, api, extraOptions) => {
    const result = await rawBaseQuery(args, api, extraOptions);
    if (result.error?.status === 401 && !isAuthEndpoint(args)) {
      redirectToLoginOnce();
    }
    return result;
  };
};
