import { getToken, saveToken } from '@/lib/auth/storage';
import { API_BASE_URL } from '@/lib/api/client';

// JWT 만료 14일 중 7일 미만 남으면 조용히 갱신 — 평소엔 로그인 화면을 거의 안 보게.
const REFRESH_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000;

const BASE64_ALPHABET =
  'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

// Hermes 는 atob/Buffer 를 전역 제공하지 않아 의존 없이 직접 디코드한다.
function decodeBase64UrlToString(segment: string): string {
  const normalized = segment.replace(/-/g, '+').replace(/_/g, '/');
  let buffer = 0;
  let bitsCollected = 0;
  let output = '';
  for (const char of normalized) {
    const charIndex = BASE64_ALPHABET.indexOf(char);
    if (charIndex === -1) continue;
    buffer = (buffer << 6) | charIndex;
    bitsCollected += 6;
    if (bitsCollected >= 8) {
      bitsCollected -= 8;
      output += String.fromCharCode((buffer >> bitsCollected) & 0xff);
    }
  }
  return output;
}

export function remainingMs(token: string, now: number): number {
  try {
    const payloadSegment = token.split('.')[1];
    if (!payloadSegment) return 0;
    const payload = JSON.parse(decodeBase64UrlToString(payloadSegment)) as {
      exp?: unknown;
    };
    if (typeof payload.exp !== 'number') return 0;
    return payload.exp * 1000 - now;
  } catch {
    return 0;
  }
}

export async function refreshSessionIfNeeded(): Promise<void> {
  const token = await getToken();
  if (!token) return;
  if (remainingMs(token, Date.now()) >= REFRESH_THRESHOLD_MS) return;

  try {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok) return;
    const body = await response.json();
    const newToken = body?.data?.token;
    if (typeof newToken === 'string' && newToken.length > 0) {
      await saveToken(newToken);
    }
  } catch {
    // 갱신 실패는 삼킨다 — 다음 API 401 에서 baseQuery 안전망이 로그아웃 처리한다.
  }
}
