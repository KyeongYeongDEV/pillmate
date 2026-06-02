import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';

export type DeviceTokenProvider = 'EXPO' | 'FCM' | 'APNS';

export interface RegisterDeviceTokenRequest {
  token: string;
  provider: DeviceTokenProvider;
}

export const userApiSlice = createApi({
  reducerPath: 'userApi',
  baseQuery: createPillmateBaseQuery(),
  tagTypes: ['Me', 'DeviceToken'],
  endpoints: (build) => ({
    registerDeviceToken: build.mutation<void, RegisterDeviceTokenRequest>({
      query: (body) => ({ url: '/users/me/device-token', method: 'POST', body }),
      invalidatesTags: ['DeviceToken'],
    }),
  }),
});

export const { useRegisterDeviceTokenMutation } = userApiSlice;
