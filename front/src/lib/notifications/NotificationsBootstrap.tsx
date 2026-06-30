import { useEffect, useRef } from 'react';
import * as Notifications from 'expo-notifications';
import { router } from 'expo-router';
import { useRegisterDeviceTokenMutation } from '@/store/slices/userApi';
import type { RegisterDeviceTokenRequest } from '@/store/slices/userApi';
import { useAppDispatch } from '@/store/hooks';
import {
  configureNotificationHandler,
  ensurePushPermission,
  fetchExpoPushToken,
  fetchNativeDeviceToken,
} from './setup';
import { extractRouteFromNotification } from './deepLink';
import { handlePushReceived } from './pushSync';

const NOTIFICATION_INBOX_ROUTE = '/notifications';

async function resolveDeviceTokenRequest(): Promise<RegisterDeviceTokenRequest | null> {
  const native = await fetchNativeDeviceToken();
  if (native) return native;
  const expoToken = await fetchExpoPushToken();
  return expoToken ? { token: expoToken, provider: 'EXPO' } : null;
}

export default function NotificationsBootstrap() {
  const [registerDeviceToken] = useRegisterDeviceTokenMutation();
  const dispatch = useAppDispatch();
  const bootstrappedRef = useRef(false);

  useEffect(() => {
    if (bootstrappedRef.current) return;
    bootstrappedRef.current = true;

    configureNotificationHandler();

    (async () => {
      const ok = await ensurePushPermission();
      if (!ok) return;
      const request = await resolveDeviceTokenRequest();
      if (!request) return;
      try {
        await registerDeviceToken(request).unwrap();
      } catch {
        // BE 미준비 시 fail-gracefully (#107 BE 측 endpoint 도착 전 대비)
      }
    })();
  }, [registerDeviceToken]);

  useEffect(() => {
    const sub = Notifications.addNotificationResponseReceivedListener((response) => {
      const route = extractRouteFromNotification(response) ?? NOTIFICATION_INBOX_ROUTE;
      router.push(route as any);
    });
    return () => sub.remove();
  }, []);

  // 포그라운드 수신 — 화면 띄운 상태에서 푸시 오면 관련 cache 즉시 invalidate
  useEffect(() => {
    const sub = Notifications.addNotificationReceivedListener((notification) => {
      handlePushReceived(notification, dispatch);
    });
    return () => sub.remove();
  }, [dispatch]);

  return null;
}
