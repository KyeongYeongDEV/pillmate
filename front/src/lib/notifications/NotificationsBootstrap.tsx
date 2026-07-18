import { useEffect, useRef } from 'react';
import * as Notifications from 'expo-notifications';
import { router } from 'expo-router';
import { useAppDispatch } from '@/store/hooks';
import { getToken } from '@/lib/auth/storage';
import {
  configureNotificationHandler,
  ensureAndroidNotificationChannels,
} from './setup';
import { registerPushForCurrentUser } from './pushRegistration';
import { extractRouteFromNotification } from './deepLink';
import { handlePushReceived } from './pushSync';

const NOTIFICATION_INBOX_ROUTE = '/notifications';

export default function NotificationsBootstrap() {
  const dispatch = useAppDispatch();
  const bootstrappedRef = useRef(false);

  useEffect(() => {
    if (bootstrappedRef.current) return;
    bootstrappedRef.current = true;

    configureNotificationHandler();
    void ensureAndroidNotificationChannels();

    // 권한요청+토큰등록은 이미 로그인된(SecureStore 토큰 보유) 복귀 사용자만 마운트 시 수행.
    // 첫 설치→미로그인 사용자는 인증이 없어 401 로 삼켜지므로 로그인 핸들러가 담당한다.
    (async () => {
      const token = await getToken();
      if (token) await registerPushForCurrentUser();
    })();
  }, []);

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
