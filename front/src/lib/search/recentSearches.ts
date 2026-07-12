import * as SecureStore from 'expo-secure-store';

const RECENT_SEARCHES_KEY = 'pillmate_recent_searches';
// 약 이름은 PII 아님 — 저장은 최대 8개(표시는 화면에서 6개로 슬라이스)
export const RECENT_SEARCHES_MAX = 8;

export async function getRecentSearches(): Promise<string[]> {
  const raw = await SecureStore.getItemAsync(RECENT_SEARCHES_KEY);
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((t): t is string => typeof t === 'string') : [];
  } catch {
    return [];
  }
}

export async function saveRecentSearches(terms: string[]): Promise<void> {
  await SecureStore.setItemAsync(RECENT_SEARCHES_KEY, JSON.stringify(terms.slice(0, RECENT_SEARCHES_MAX)));
}

export async function clearRecentSearches(): Promise<void> {
  await SecureStore.deleteItemAsync(RECENT_SEARCHES_KEY);
}

// 신규 검색어를 맨 앞에 추가 — 중복 제거, 최대 개수 cap
export function pushRecentSearch(current: string[], term: string): string[] {
  const trimmed = term.trim();
  if (!trimmed) return current;
  const deduped = current.filter(t => t !== trimmed);
  return [trimmed, ...deduped].slice(0, RECENT_SEARCHES_MAX);
}
