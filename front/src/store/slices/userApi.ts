import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';

export type DeviceTokenProvider = 'EXPO' | 'FCM';

export interface RegisterDeviceTokenRequest {
  token: string;
  provider: DeviceTokenProvider;
}

export interface UpdateUserNameRequest {
  name: string;
}

export interface UserProfile {
  name: string;
  email: string | null;
  profileUrl: string | null;
}

const EMPTY_PROFILE: UserProfile = { name: '', email: null, profileUrl: null };

export const userApiSlice = createApi({
  reducerPath: 'userApi',
  baseQuery: createPillmateBaseQuery(),
  tagTypes: ['Me', 'DeviceToken'],
  endpoints: (build) => ({
    registerDeviceToken: build.mutation<void, RegisterDeviceTokenRequest>({
      query: (body) => ({ url: '/users/me/device-token', method: 'POST', body }),
      invalidatesTags: ['DeviceToken'],
    }),
    updateUserName: build.mutation<UserProfile, UpdateUserNameRequest>({
      query: (body) => ({ url: '/users/me', method: 'PATCH', body }),
      transformResponse: (response: ApiEnvelope<UserProfile>) => response?.data ?? EMPTY_PROFILE,
      invalidatesTags: ['Me'],
    }),
  }),
});

export const { useRegisterDeviceTokenMutation, useUpdateUserNameMutation } = userApiSlice;
