import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import type { NotificationItem } from '@/types/notification';

export function markReadInList(list: NotificationItem[], id: number): void {
  const target = list.find((n) => n.id === id);
  if (target) target.status = 'READ';
}

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
      async onQueryStarted(id, { dispatch, queryFulfilled }) {
        const patch = dispatch(
          notificationApiSlice.util.updateQueryData('getNotifications', undefined, (draft) => {
            markReadInList(draft, id);
          }),
        );
        try {
          await queryFulfilled;
        } catch {
          patch.undo();
        }
      },
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
