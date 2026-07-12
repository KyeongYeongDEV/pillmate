import { activityApi } from '@/store/slices/activityApi';
import { caregroupApiSlice } from '@/store/slices/caregroupApi';
import { notificationApiSlice } from '@/store/slices/notificationApi';

interface ReceivedNotification {
  request?: { content?: { data?: Record<string, unknown> | null } };
}

// 포그라운드 푸시 수신 시 cache invalidate — DOSE_* 면 활동/그룹 피드, GROUP_* 면 그룹 상세(신규 멤버 등 즉시 반영), 항상 알림 인박스.
export function handlePushReceived(
  notification: ReceivedNotification,
  dispatch: (action: any) => void,
) {
  const type = notification?.request?.content?.data?.type as string | undefined;
  if (typeof type === 'string' && type.startsWith('DOSE_')) {
    dispatch(activityApi.util.invalidateTags(['Activity']));
    dispatch(caregroupApiSlice.util.invalidateTags(['Group', 'GroupDetail']));
  }
  if (typeof type === 'string' && type.startsWith('GROUP_')) {
    dispatch(caregroupApiSlice.util.invalidateTags(['Group', 'GroupDetail']));
  }
  dispatch(notificationApiSlice.util.invalidateTags(['Notification']));
}
