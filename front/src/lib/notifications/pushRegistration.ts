import { store } from '@/store';
import { userApiSlice } from '@/store/slices/userApi';
import type { RegisterDeviceTokenRequest } from '@/store/slices/userApi';
import {
  ensurePushPermission,
  fetchExpoPushToken,
  fetchNativeDeviceToken,
} from './setup';

async function resolveDeviceTokenRequest(): Promise<RegisterDeviceTokenRequest | null> {
  const native = await fetchNativeDeviceToken();
  if (native) return native;
  const expoToken = await fetchExpoPushToken();
  return expoToken ? { token: expoToken, provider: 'EXPO' } : null;
}

let lastRegisteredToken: string | null = null;

// 로그아웃 시 호출 — 다음 로그인 사용자가 같은 기기 토큰이라도 재등록되도록 세션 가드를 리셋한다.
export function resetPushRegistration(): void {
  lastRegisteredToken = null;
}

// POST /users/me/device-token 은 인증 필요 — 인증이 확보된 시점(로그인 성공 직후, 또는 토큰 보유 복귀)에만 호출한다.
// 성공한 토큰만 기억해 세션 내 중복 등록을 막고, 실패(미인증·네트워크)는 다음 호출에서 재시도되게 둔다.
export async function registerPushForCurrentUser(): Promise<void> {
  const granted = await ensurePushPermission();
  if (!granted) return;
  const request = await resolveDeviceTokenRequest();
  if (!request) return;
  if (request.token === lastRegisteredToken) return;
  try {
    await store
      .dispatch(userApiSlice.endpoints.registerDeviceToken.initiate(request))
      .unwrap();
    lastRegisteredToken = request.token;
  } catch {
    // 미인증/네트워크 실패 — 조용히 무시. 다음 로그인/복귀 시 재시도된다.
  }
}
