import type { SlotEditView } from '@/types/schedule';

function timeToMinutes(time: string): number {
  const [hour, minute] = time.split(':');
  return parseInt(hour, 10) * 60 + parseInt(minute, 10);
}

export function sortSlotsByTime(slots: SlotEditView[]): SlotEditView[] {
  return [...slots].sort((a, b) => timeToMinutes(a.time) - timeToMinutes(b.time));
}
