import { router } from 'expo-router';

const DEBOUNCE_MS = 600;
const lastPushAt: Record<string, number> = {};

/**
 * router.push with per-route debounce guard.
 * Prevents duplicate stack entries when a user taps a navigation trigger rapidly.
 */
export function safePush(path: string, params?: Record<string, string>): void {
  const now = Date.now();
  if (lastPushAt[path] && now - lastPushAt[path] < DEBOUNCE_MS) return;
  lastPushAt[path] = now;
  if (params) {
    router.push({ pathname: path as any, params });
  } else {
    router.push(path as any);
  }
}
