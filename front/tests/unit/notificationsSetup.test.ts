const mockSetHandler = jest.fn();
const mockGetPerm = jest.fn();
const mockReqPerm = jest.fn();
const mockGetToken = jest.fn();
const mockGetDeviceToken = jest.fn();

jest.mock('expo-notifications', () => ({
  setNotificationHandler: (...args: any[]) => mockSetHandler(...args),
  getPermissionsAsync: () => mockGetPerm(),
  requestPermissionsAsync: () => mockReqPerm(),
  getExpoPushTokenAsync: (opts: any) => mockGetToken(opts),
  getDevicePushTokenAsync: () => mockGetDeviceToken(),
}));

jest.mock('react-native', () => ({
  Platform: { OS: 'android' },
}));

jest.mock('expo-constants', () => ({
  __esModule: true,
  default: { expoConfig: { extra: { eas: { projectId: 'test-project-id' } } } },
}));

jest.mock('expo-device', () => ({
  isDevice: true,
}));

import { Platform } from 'react-native';
import {
  ensurePushPermission,
  fetchExpoPushToken,
  fetchNativeDeviceToken,
  configureNotificationHandler,
} from '@/lib/notifications/setup';

describe('notifications/setup', () => {
  beforeEach(() => {
    mockSetHandler.mockReset();
    mockGetPerm.mockReset();
    mockReqPerm.mockReset();
    mockGetToken.mockReset();
    mockGetDeviceToken.mockReset();
    (Platform as any).OS = 'android';
  });

  it('configureNotificationHandler — foreground banner handler 등록', () => {
    configureNotificationHandler();
    expect(mockSetHandler).toHaveBeenCalledTimes(1);
  });

  it('ensurePushPermission — 이미 granted 시 추가 request X', async () => {
    mockGetPerm.mockResolvedValue({ status: 'granted' });
    const ok = await ensurePushPermission();
    expect(ok).toBe(true);
    expect(mockReqPerm).not.toHaveBeenCalled();
  });

  it('ensurePushPermission — undetermined 시 request 후 granted', async () => {
    mockGetPerm.mockResolvedValue({ status: 'undetermined' });
    mockReqPerm.mockResolvedValue({ status: 'granted' });
    const ok = await ensurePushPermission();
    expect(mockReqPerm).toHaveBeenCalledTimes(1);
    expect(ok).toBe(true);
  });

  it('ensurePushPermission — denied 시 false 반환', async () => {
    mockGetPerm.mockResolvedValue({ status: 'denied' });
    const ok = await ensurePushPermission();
    expect(ok).toBe(false);
    expect(mockReqPerm).not.toHaveBeenCalled();
  });

  it('fetchExpoPushToken — projectId 주입 후 token 반환', async () => {
    mockGetToken.mockResolvedValue({ data: 'ExponentPushToken[abc123]' });
    const token = await fetchExpoPushToken();
    expect(mockGetToken).toHaveBeenCalledWith({ projectId: 'test-project-id' });
    expect(token).toBe('ExponentPushToken[abc123]');
  });

  it('fetchExpoPushToken — getExpoPushTokenAsync 실패 시 null', async () => {
    mockGetToken.mockRejectedValue(new Error('network'));
    const token = await fetchExpoPushToken();
    expect(token).toBeNull();
  });

  it('fetchNativeDeviceToken — android 시 FCM provider 로 토큰 반환', async () => {
    (Platform as any).OS = 'android';
    mockGetDeviceToken.mockResolvedValue({ type: 'android', data: 'fcm-xyz' });
    const result = await fetchNativeDeviceToken();
    expect(result).toEqual({ token: 'fcm-xyz', provider: 'FCM' });
  });

  it('fetchNativeDeviceToken — ios 시 네이티브 미지원으로 null (Expo 폴백 위임)', async () => {
    (Platform as any).OS = 'ios';
    const result = await fetchNativeDeviceToken();
    expect(result).toBeNull();
    expect(mockGetDeviceToken).not.toHaveBeenCalled();
  });

  it('fetchNativeDeviceToken — 미지원 플랫폼(web) 시 호출 없이 null', async () => {
    (Platform as any).OS = 'web';
    const result = await fetchNativeDeviceToken();
    expect(result).toBeNull();
    expect(mockGetDeviceToken).not.toHaveBeenCalled();
  });

  it('fetchNativeDeviceToken — 빈 토큰 시 null', async () => {
    mockGetDeviceToken.mockResolvedValue({ type: 'android', data: '' });
    const result = await fetchNativeDeviceToken();
    expect(result).toBeNull();
  });

  it('fetchNativeDeviceToken — getDevicePushTokenAsync 예외 시 null', async () => {
    mockGetDeviceToken.mockRejectedValue(new Error('no native module'));
    const result = await fetchNativeDeviceToken();
    expect(result).toBeNull();
  });
});
