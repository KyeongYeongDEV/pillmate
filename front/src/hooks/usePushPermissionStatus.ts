import { useCallback, useEffect, useState } from 'react';
import { AppState } from 'react-native';
import * as Notifications from 'expo-notifications';
import type { PushPermissionStatus } from '@/lib/notifications/permissionGuide';

// 설정 화면에서 알림을 켜고 돌아오는 경로를 잡기 위해 포그라운드 복귀마다 재조회한다.
// (토큰 재등록은 NotificationsBootstrap 의 AppState 'active' 핸들러가 담당)
export function usePushPermissionStatus(): PushPermissionStatus {
  const [status, setStatus] = useState<PushPermissionStatus>('unknown');

  const refresh = useCallback(async () => {
    try {
      const permission = await Notifications.getPermissionsAsync();
      setStatus((permission?.status as PushPermissionStatus) ?? 'unknown');
    } catch {
      setStatus('unknown');
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') void refresh();
    });
    return () => subscription.remove();
  }, [refresh]);

  return status;
}
