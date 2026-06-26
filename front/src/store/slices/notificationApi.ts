import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import type { NotificationItem } from '@/types/notification';

export const notificationApiSlice = createApi({
  reducerPath: 'notificationApi',
  baseQuery: createPillmateBaseQuery(),
  tagTypes: ['Notification'],
  endpoints: (build) => ({
    getNotifications: build.query<NotificationItem[], void>({
      query: () => '/notifications',
      transformResponse: (response: ApiEnvelope<NotificationItem[]>) => response?.data ?? [],
      providesTags: ['Notification'],
    }),
    markRead: build.mutation<void, number>({
      query: (id) => ({ url: `/notifications/${id}/read`, method: 'PATCH' }),
      invalidatesTags: ['Notification'],
    }),
    markReadAll: build.mutation<void, void>({
      query: () => ({ url: '/notifications/read-all', method: 'PATCH' }),
      invalidatesTags: ['Notification'],
    }),
  }),
});

export const {
  useGetNotificationsQuery,
  useMarkReadMutation,
  useMarkReadAllMutation,
} = notificationApiSlice;
