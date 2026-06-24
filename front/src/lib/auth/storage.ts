import * as SecureStore from "expo-secure-store";
import { AUTH_STORAGE_KEY } from "@/lib/constants";

const USER_ID_KEY = "pillmate_user_id";
export const ONBOARDING_SEEN_KEY = "onboarding_seen";

// 환자 PII 가 포함될 JWT 는 SecureStore 에만 저장 (AsyncStorage 평문 금지).
export async function saveToken(token: string): Promise<void> {
  await SecureStore.setItemAsync(AUTH_STORAGE_KEY, token);
}

export async function getToken(): Promise<string | null> {
  return SecureStore.getItemAsync(AUTH_STORAGE_KEY);
}

export async function clearToken(): Promise<void> {
  await SecureStore.deleteItemAsync(AUTH_STORAGE_KEY);
}

export async function setCurrentUserId(id: number): Promise<void> {
  await SecureStore.setItemAsync(USER_ID_KEY, String(id));
}

export async function clearCurrentUserId(): Promise<void> {
  await SecureStore.deleteItemAsync(USER_ID_KEY);
}

// 저장값 우선, 없으면 dev 폴백(seed userId=1) 유지 — 하위호환
export async function getCurrentUserId(): Promise<number | null> {
  const stored = await SecureStore.getItemAsync(USER_ID_KEY);
  if (stored != null) return Number(stored);
  return 1;
}

export async function clearAuth(): Promise<void> {
  await clearToken();
  await clearCurrentUserId();
}
