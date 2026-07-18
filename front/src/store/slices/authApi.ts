import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import { saveToken, setCurrentUserId, saveDisplayName } from '@/lib/auth/storage';
import { resolveDevUserId } from '@/lib/auth/devUserId';

export interface KakaoLoginRequest {
  code: string;
  redirectUri: string;
}

export interface AuthProfile {
  name: string;
  email: string;
  profileUrl: string | null;
}

export interface AuthResult {
  token: string;
  userId: number;
  isNewUser: boolean;
  profile: AuthProfile;
}

const EMPTY_RESULT: AuthResult = {
  token: '', userId: 0, isNewUser: false,
  profile: { name: '', email: '', profileUrl: null },
};

// dev fallback: resolveDevUserId() 있으면 X-Dev-User-Id 주입(env override → Platform.OS 분기).
// prod 는 BE 가 PILLMATE_DEV_FALLBACK=false 일 때 무시(보안).
export function devUserIdHeaders(): Record<string, string> {
  const devUserId = resolveDevUserId();
  return devUserId ? { 'X-Dev-User-Id': devUserId } : {};
}

export const authApiSlice = createApi({
  reducerPath: 'authApi',
  baseQuery: createPillmateBaseQuery(),
  endpoints: (build) => ({
    kakaoLogin: build.mutation<AuthResult, KakaoLoginRequest>({
      query: (body) => ({ url: '/auth/kakao', method: 'POST', body, headers: devUserIdHeaders() }),
      transformResponse: (response: ApiEnvelope<AuthResult>) =>
        response?.data ?? EMPTY_RESULT,
      async onQueryStarted(_arg, { queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          if (data.token) await saveToken(data.token);
          if (data.userId) await setCurrentUserId(data.userId);
          if (data.profile?.name) await saveDisplayName(data.profile.name);
        } catch {
          // 로그인 실패 — 컴포넌트에서 처리
        }
      },
    }),

    exchangeKakaoCode: build.mutation<AuthResult, { loginCode: string }>({
      query: (body) => ({ url: '/auth/kakao/exchange', method: 'POST', body, headers: devUserIdHeaders() }),
      transformResponse: (response: ApiEnvelope<AuthResult>) =>
        response?.data ?? EMPTY_RESULT,
      async onQueryStarted(_arg, { queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          if (data.token) await saveToken(data.token);
          if (data.userId) await setCurrentUserId(data.userId);
          if (data.profile?.name) await saveDisplayName(data.profile.name);
        } catch {
          // 교환 실패 — 컴포넌트에서 처리
        }
      },
    }),

    // 네이티브 카카오 SDK 로그인: 클라이언트가 accessToken 을 직접 받아 BE 로 전달.
    kakaoNativeLogin: build.mutation<AuthResult, { accessToken: string }>({
      query: (body) => ({ url: '/auth/kakao/native', method: 'POST', body, headers: devUserIdHeaders() }),
      transformResponse: (response: ApiEnvelope<AuthResult>) =>
        response?.data ?? EMPTY_RESULT,
      async onQueryStarted(_arg, { queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          if (data.token) await saveToken(data.token);
          if (data.userId) await setCurrentUserId(data.userId);
          if (data.profile?.name) await saveDisplayName(data.profile.name);
        } catch {
          // 로그인 실패 — 컴포넌트에서 처리
        }
      },
    }),
  }),
});

export const {
  useKakaoLoginMutation,
  useExchangeKakaoCodeMutation,
  useKakaoNativeLoginMutation,
} = authApiSlice;
