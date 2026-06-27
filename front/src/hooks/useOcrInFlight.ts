import { useCallback } from 'react';
import * as Crypto from 'expo-crypto';

export const OCR_IN_FLIGHT_TTL_MS = 5 * 60 * 1000;
export const OCR_SLOW_THRESHOLD_MS = 30 * 1000;
export const OCR_RETRY_THRESHOLD_MS = 60 * 1000;

interface InFlightEntry {
  startTime: number;
  attempts: number;
}

// 세션 내 메모리 레지스트리 — 같은 이미지의 OCR 요청이 진행 중이면 중복 호출을 차단한다.
// AsyncStorage 대신 모듈 싱글톤: in-flight 상태는 앱 재시작을 넘겨 보존될 필요가 없고,
// 화면 전환(camera→confirm→back)에는 모듈 상태가 그대로 유지된다.
const registry = new Map<string, InFlightEntry>();

export interface BeginResult {
  allowed: boolean; // true = 호출자가 소유자, 실제 OCR 진행
  elapsedMs: number; // 진행 중 요청의 경과 시간 (신규면 0)
  attempts: number;
}

function isStale(entry: InFlightEntry, nowMs: number): boolean {
  return nowMs - entry.startTime > OCR_IN_FLIGHT_TTL_MS;
}

export function beginOcr(hash: string, nowMs: number): BeginResult {
  const existing = registry.get(hash);
  if (existing && !isStale(existing, nowMs)) {
    existing.attempts += 1;
    return { allowed: false, elapsedMs: nowMs - existing.startTime, attempts: existing.attempts };
  }
  registry.set(hash, { startTime: nowMs, attempts: 1 });
  return { allowed: true, elapsedMs: 0, attempts: 1 };
}

export function endOcr(hash: string): void {
  registry.delete(hash);
}

export function isOcrInFlight(hash: string, nowMs: number): boolean {
  const entry = registry.get(hash);
  if (!entry) return false;
  if (isStale(entry, nowMs)) {
    registry.delete(hash);
    return false;
  }
  return true;
}

export function resetOcrRegistry(): void {
  registry.clear();
}

export async function hashImageUri(uri: string): Promise<string> {
  return Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, uri);
}

export function useOcrInFlight() {
  const begin = useCallback((hash: string) => beginOcr(hash, Date.now()), []);
  const end = useCallback((hash: string) => endOcr(hash), []);
  return { begin, end, hashImageUri };
}
