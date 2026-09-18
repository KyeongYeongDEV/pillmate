import type { NotificationResponse } from 'expo-notifications';

export function extractRouteFromNotification(
  response: NotificationResponse | null | undefined,
): string | null {
  const data = response?.notification?.request?.content?.data;
  if (!data) return null;
  const route = (data as Record<string, unknown>).route;
  return typeof route === 'string' && route.length > 0 ? route : null;
}

// 푸시 탭으로 앱 진입 시 해당 알림을 읽음 처리하기 위한 서버 notification id 추출.
export function extractNotificationIdFromNotification(
  response: NotificationResponse | null | undefined,
): number | null {
  const data = response?.notification?.request?.content?.data;
  if (!data) return null;
  const raw = (data as Record<string, unknown>).notificationId;
  if (typeof raw !== 'string') return null;
  const id = Number(raw);
  return Number.isFinite(id) ? id : null;
}
