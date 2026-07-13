import * as Notifications from 'expo-notifications';
import Constants from 'expo-constants';
import { Platform } from 'react-native';
import type { DeviceTokenProvider } from '@/store/slices/userApi';

export interface NativeDeviceToken {
  token: string;
  provider: DeviceTokenProvider;
}

const NATIVE_PROVIDER_BY_PLATFORM: Partial<Record<typeof Platform.OS, DeviceTokenProvider>> = {
  android: 'FCM',
};

export function configureNotificationHandler(): void {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowAlert: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
      shouldShowBanner: true,
      shouldShowList: true,
    }),
  });
}

const DOSE_REMINDER_CHANNEL_ID = 'dose-reminder';
const GROUP_ACTIVITY_CHANNEL_ID = 'group-activity';

// Android 8+ 는 채널 없이 발송되면 fallback(기본 importance) 로 흘러가 종료 상태 표시가 막힘 — 부팅 시 멱등 생성.
export async function ensureAndroidNotificationChannels(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(DOSE_REMINDER_CHANNEL_ID, {
    name: '복약 리마인더',
    importance: Notifications.AndroidImportance.MAX,
    sound: 'default',
    vibrationPattern: [0, 250, 250, 250],
    lockscreenVisibility: Notifications.AndroidNotificationVisibility.PUBLIC,
  });
  await Notifications.setNotificationChannelAsync(GROUP_ACTIVITY_CHANNEL_ID, {
    name: '그룹 활동',
    importance: Notifications.AndroidImportance.DEFAULT,
  });
}

export async function ensurePushPermission(): Promise<boolean> {
  const current = await Notifications.getPermissionsAsync();
  if (current.status === 'granted') return true;
  if (current.status === 'denied') return false;
  const next = await Notifications.requestPermissionsAsync();
  return next.status === 'granted';
}

export async function fetchNativeDeviceToken(): Promise<NativeDeviceToken | null> {
  const provider = NATIVE_PROVIDER_BY_PLATFORM[Platform.OS];
  if (!provider) return null;
  try {
    const { data } = await Notifications.getDevicePushTokenAsync();
    if (typeof data !== 'string' || data.length === 0) return null;
    return { token: data, provider };
  } catch {
    return null;
  }
}

export async function fetchExpoPushToken(): Promise<string | null> {
  const projectId =
    Constants.expoConfig?.extra?.eas?.projectId ??
    (Constants as any).easConfig?.projectId;
  if (!projectId) return null;
  try {
    const { data } = await Notifications.getExpoPushTokenAsync({ projectId });
    return data ?? null;
  } catch {
    return null;
  }
}
