import type { PrescriptionTimeOfDay } from '@/types/prescription';

const MORNING_END_HOUR = 11;
const NOON_END_HOUR = 17;

// 시각(HH:mm 또는 HH:mm:ss) → timeOfDay bucket. 백엔드 enum·활동피드 grouping 용(UI 비노출).
export function deriveTimeOfDay(hhmmss: string): PrescriptionTimeOfDay {
  const hour = parseInt(hhmmss.slice(0, 2), 10);
  if (hour < MORNING_END_HOUR) return 'MORNING';
  if (hour < NOON_END_HOUR) return 'NOON';
  return 'EVENING';
}
