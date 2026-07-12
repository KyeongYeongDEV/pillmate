import type { FetchBaseQueryError } from '@reduxjs/toolkit/query';
import type { ApiEnvelope } from '@/lib/api/client';

export function joinGroupErrorMessage(error: unknown): string {
  const err = error as FetchBaseQueryError | undefined;
  if (err && typeof err === 'object' && 'status' in err) {
    if (err.status === 410) return '만료되었거나 잘못된 코드예요';
    if (err.status === 404) return '초대 코드를 찾을 수 없어요';
    if (err.status === 'TIMEOUT_ERROR') return '연결 시간이 초과됐어요. 네트워크를 확인해주세요';
    const envelope = (err as { data?: ApiEnvelope<unknown> }).data;
    if (envelope?.error?.message) return envelope.error.message;
  }
  return '초대 코드가 올바르지 않아요';
}
