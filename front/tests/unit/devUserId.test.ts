import { Platform } from 'react-native';
import { resolveDevUserId } from '@/lib/auth/devUserId';

describe('resolveDevUserId — env override → Platform.OS 분기', () => {
  const ORIGINAL_ENV = process.env.EXPO_PUBLIC_DEV_USER_ID;
  const ORIGINAL_OS = Platform.OS;

  afterEach(() => {
    if (ORIGINAL_ENV === undefined) delete process.env.EXPO_PUBLIC_DEV_USER_ID;
    else process.env.EXPO_PUBLIC_DEV_USER_ID = ORIGINAL_ENV;
    (Platform as { OS: string }).OS = ORIGINAL_OS;
  });

  it('iOS + env 없음 → null (헤더 미주입, seed user1)', () => {
    delete process.env.EXPO_PUBLIC_DEV_USER_ID;
    (Platform as { OS: string }).OS = 'ios';
    expect(resolveDevUserId()).toBeNull();
  });

  it('Android + env 없음 → "2" (X-Dev-User-Id user2)', () => {
    delete process.env.EXPO_PUBLIC_DEV_USER_ID;
    (Platform as { OS: string }).OS = 'android';
    expect(resolveDevUserId()).toBe('2');
  });

  it('env override → Platform 무관 그 값 ("3")', () => {
    process.env.EXPO_PUBLIC_DEV_USER_ID = '3';
    (Platform as { OS: string }).OS = 'ios';
    expect(resolveDevUserId()).toBe('3');
    (Platform as { OS: string }).OS = 'android';
    expect(resolveDevUserId()).toBe('3');
  });
});
