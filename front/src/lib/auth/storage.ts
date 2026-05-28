import * as SecureStore from "expo-secure-store";

import { AUTH_STORAGE_KEY } from "@/lib/constants";

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

// Phase 1: 하드코딩 1 반환. Phase T-BE-USER-AUTH 에서 SecureStore 조회로 교체 예정.
export async function getCurrentUserId(): Promise<number | null> {
  return 1;
}
