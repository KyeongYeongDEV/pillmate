import type { MedSlot } from '@/types/schedule';
import type { TimeSlot } from '@/components/home/TimeSlotCards';

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
