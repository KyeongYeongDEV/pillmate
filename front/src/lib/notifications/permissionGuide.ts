import * as SecureStore from 'expo-secure-store';

export type PushPermissionStatus = 'unknown' | 'granted' | 'denied' | 'undetermined';

const DISMISSED_AT_KEY = 'pillmate_push_guide_dismissed_at';

// 안내는 하루 1회까지만 — 매 홈 진입마다 다시 뜨면 성가시다(사용자 이탈 위험).
export const PUSH_GUIDE_SNOOZE_MS = 24 * 60 * 60 * 1000;

export function shouldShowPushGuide(
  status: PushPermissionStatus,
  dismissedAt: number | null,
  now: number,
): boolean {
  if (status !== 'denied') return false;
  if (dismissedAt == null) return true;
  const elapsed = now - dismissedAt;
  return elapsed < 0 || elapsed >= PUSH_GUIDE_SNOOZE_MS;
}

export async function getPushGuideDismissedAt(): Promise<number | null> {
  try {
    const raw = await SecureStore.getItemAsync(DISMISSED_AT_KEY);
    if (!raw) return null;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export async function savePushGuideDismissedAt(at: number): Promise<void> {
  try {
    await SecureStore.setItemAsync(DISMISSED_AT_KEY, String(at));
  } catch {
    // 저장 실패는 안내 재노출로 이어질 뿐 기능 손상 없음 — 조용히 무시.
  }
}
