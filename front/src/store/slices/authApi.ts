import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import { saveToken, setCurrentUserId } from '@/lib/auth/storage';

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

export const authApiSlice = createApi({
  reducerPath: 'authApi',
  baseQuery: createPillmateBaseQuery(),
  endpoints: (build) => ({
    kakaoLogin: build.mutation<AuthResult, KakaoLoginRequest>({
      query: (body) => ({ url: '/auth/kakao', method: 'POST', body }),
      transformResponse: (response: ApiEnvelope<AuthResult>) =>
        response?.data ?? EMPTY_RESULT,
      async onQueryStarted(_arg, { queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          if (data.token) await saveToken(data.token);
          if (data.userId) await setCurrentUserId(data.userId);
        } catch {
          // 로그인 실패 — 컴포넌트에서 처리
        }
      },
    }),
  }),
});

export const { useKakaoLoginMutation } = authApiSlice;
