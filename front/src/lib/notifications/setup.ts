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
