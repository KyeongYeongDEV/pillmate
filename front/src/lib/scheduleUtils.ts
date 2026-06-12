import type { MedSlot } from '@/types/schedule';
import type { TimeSlot } from '@/components/home/TimeSlotCards';

const HEADLINE_NO_DOSE = '오늘은 드실 약이 없어요';
const HEADLINE_ALL_DONE = '오늘 약을 모두 드셨어요 👏';

export function buildDoseHeadline(totalCount: number, doneCount: number): string {
  if (totalCount === 0) return HEADLINE_NO_DOSE;
  if (doneCount === totalCount) return HEADLINE_ALL_DONE;
  return `오늘 약 ${totalCount}번 중 ${doneCount}번 드셨어요`;
}

export function medSlotToTimeSlot(slot: MedSlot): TimeSlot {
  return {
    id: slot.id,
    label: slot.label,
    time: slot.time,
    state: slot.state as TimeSlot['state'],
    drugCount: slot.drugCount ?? slot.items.length,
    pillColors: slot.pillColors ?? [],
    doseLogId: slot.doseLogId,
  };
}
