import type { NotificationResponse } from 'expo-notifications';

export function extractRouteFromNotification(
  response: NotificationResponse | null | undefined,
): string | null {
  const data = response?.notification?.request?.content?.data;
  if (!data) return null;
  const route = (data as Record<string, unknown>).route;
  return typeof route === 'string' && route.length > 0 ? route : null;
}
