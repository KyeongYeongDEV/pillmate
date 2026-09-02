import type { NotificationItem } from '@/types/notification';

export interface NudgeResult {
  alreadyNotified: boolean;
}

const NUDGE_SUCCESS_SENT = '약 드시라고 알림을 보냈어요';
const NUDGE_SUCCESS_ALREADY = '이미 복약 알림이 전달됐어요';

const NUDGE_ERROR_BY_STATUS: Record<number, string> = {
  429: '방금 알림을 보냈어요. 잠시 후 다시 시도해 주세요.',
  409: '이미 복용한 약이에요.',
  403: '알림을 보낼 권한이 없어요.',
};
const NUDGE_ERROR_FALLBACK = '알림을 보내지 못했어요. 잠시 후 다시 시도해 주세요.';

// 다른 그룹원이 약을 안 먹은 걸(DOSE_OVERDUE) 내가 봤을 때만 넛지 가능. 내 자신은 넛지 불가.
export function canNudge(item: NotificationItem, currentUserId: number | null): boolean {
  return (
    item.type === 'DOSE_OVERDUE' &&
    item.doseLogId != null &&
    item.actorUserId != null &&
    currentUserId != null &&
    item.actorUserId !== currentUserId
  );
}

export function nudgeSuccessMessage(result: NudgeResult): string {
  return result.alreadyNotified ? NUDGE_SUCCESS_ALREADY : NUDGE_SUCCESS_SENT;
}

export function nudgeErrorMessage(status: number | undefined): string {
  if (status != null && NUDGE_ERROR_BY_STATUS[status]) return NUDGE_ERROR_BY_STATUS[status];
  return NUDGE_ERROR_FALLBACK;
}
