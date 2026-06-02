const mockSetHandler = jest.fn();
const mockGetPerm = jest.fn();
const mockReqPerm = jest.fn();
const mockGetToken = jest.fn();

jest.mock('expo-notifications', () => ({
  setNotificationHandler: (...args: any[]) => mockSetHandler(...args),
  getPermissionsAsync: () => mockGetPerm(),
  requestPermissionsAsync: () => mockReqPerm(),
  getExpoPushTokenAsync: (opts: any) => mockGetToken(opts),
}));

jest.mock('expo-constants', () => ({
  __esModule: true,
  default: { expoConfig: { extra: { eas: { projectId: 'test-project-id' } } } },
}));

jest.mock('expo-device', () => ({
  isDevice: true,
}));

import { ensurePushPermission, fetchExpoPushToken, configureNotificationHandler } from '@/lib/notifications/setup';

describe('notifications/setup', () => {
  beforeEach(() => {
    mockSetHandler.mockReset();
    mockGetPerm.mockReset();
    mockReqPerm.mockReset();
    mockGetToken.mockReset();
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
});
