import { useEffect, useRef } from 'react';
import * as Notifications from 'expo-notifications';
import { router } from 'expo-router';
import { useRegisterDeviceTokenMutation } from '@/store/slices/userApi';
import {
  configureNotificationHandler,
  ensurePushPermission,
  fetchExpoPushToken,
} from './setup';
import { extractRouteFromNotification } from './deepLink';

export default function NotificationsBootstrap() {
  const [registerDeviceToken] = useRegisterDeviceTokenMutation();
  const bootstrappedRef = useRef(false);

  useEffect(() => {
    if (bootstrappedRef.current) return;
    bootstrappedRef.current = true;

    configureNotificationHandler();

    (async () => {
      const ok = await ensurePushPermission();
      if (!ok) return;
      const token = await fetchExpoPushToken();
      if (!token) return;
      try {
        await registerDeviceToken({ token, provider: 'EXPO' }).unwrap();
      } catch {
        // BE 미준비 시 fail-gracefully (#107 BE 측 endpoint 도착 전 대비)
      }
    })();
  }, [registerDeviceToken]);

  useEffect(() => {
    const sub = Notifications.addNotificationResponseReceivedListener((response) => {
      const route = extractRouteFromNotification(response);
      if (route) router.push(route as any);
    });
    return () => sub.remove();
  }, []);

  return null;
}
