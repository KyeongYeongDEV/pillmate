import type { MedSlot } from '@/types/schedule';
import type { TimeSlot } from '@/components/home/TimeSlotCards';

const HEADLINE_NO_DOSE = '오늘은 드실 약이 없어요';
const HEADLINE_ALL_DONE = '오늘 복약 끝!';
export const STREAK_DISPLAY_MIN = 2;

export interface HeadlineSlot {
  time: string;
  label: string;
  state: string;
}

export type SlotDotStatus = 'done' | 'next' | 'wait' | 'missed';

export function buildDoseHeadline(slots: HeadlineSlot[], now: Date, streak = 0): string {
  if (slots.length === 0) return HEADLINE_NO_DOSE;
  const undone = slots.filter(s => s.state !== 'done');
  if (undone.length === 0) return appendStreak(HEADLINE_ALL_DONE, streak);
  const nowMinutes = toMinutesOfDay(now);
  const missed = undone.find(s => toMinutes(s.time) < nowMinutes);
  if (missed) return `${missed.label}약 기록이 없어요 · 드셨다면 체크해 주세요`;
  const next = undone[0];
  if (nowMinutes < toMinutes(slots[0].time)) {
    return `${formatSlotTime(next.time)} ${next.label}약으로 시작해요`;
  }
  return `다음은 ${formatSlotTime(next.time)} ${next.label}약이에요`;
}

export function deriveSlotStatuses(slots: HeadlineSlot[], now: Date): SlotDotStatus[] {
  const nowMinutes = toMinutesOfDay(now);
  let nextAssigned = false;
  return slots.map(s => {
    if (s.state === 'done') return 'done';
    if (toMinutes(s.time) < nowMinutes) return 'missed';
    if (nextAssigned) return 'wait';
    nextAssigned = true;
    return 'next';
  });
}

function appendStreak(headline: string, streak: number): string {
  return streak >= STREAK_DISPLAY_MIN ? `${headline} ${streak}일 연속 달성 🔥` : headline;
}

function toMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

function toMinutesOfDay(now: Date): number {
  return now.getHours() * 60 + now.getMinutes();
}

function formatSlotTime(time: string): string {
  const [h, m] = time.split(':').map(Number);
  return m === 0 ? `${h}시` : `${h}시 ${m}분`;
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
