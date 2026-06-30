import { Platform } from 'react-native';

// dev 모드 user 관점 선택 (시뮬=user1 / 에뮬·실기기=user2 동시 테스트).
// 1순위: EXPO_PUBLIC_DEV_USER_ID override. 2순위: Platform.OS (android→'2', iOS→null=헤더 미주입).
// prod 무력화는 BE 책임(PILLMATE_DEV_FALLBACK=false).
export function resolveDevUserId(): string | null {
  const override = process.env.EXPO_PUBLIC_DEV_USER_ID;
  if (override) return override;
  return Platform.OS === 'android' ? '2' : null;
}
