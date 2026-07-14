import { sortSlotsByTime } from '../../src/lib/prescription/sortSlots';
import type { SlotEditView } from '../../src/types/schedule';

function slot(scheduleId: number, time: string): SlotEditView {
  return { scheduleId, timeOfDay: 'MORNING', time, endDate: '2026-12-31', editable: true };
}

describe('sortSlotsByTime — 알림 시간 오름차순 정렬', () => {
  it('추가 순서와 무관하게 시간 오름차순으로 정렬', () => {
    const input = [slot(1, '19:00'), slot(2, '08:00'), slot(3, '12:30')];
    expect(sortSlotsByTime(input).map(s => s.time)).toEqual(['08:00', '12:30', '19:00']);
  });

  it('원본 배열을 변형하지 않음 (파생값)', () => {
    const input = [slot(1, '19:00'), slot(2, '08:00')];
    sortSlotsByTime(input);
    expect(input.map(s => s.time)).toEqual(['19:00', '08:00']);
  });

  it('HH:mm:ss 포맷도 처리', () => {
    const input = [slot(1, '22:00:00'), slot(2, '07:30:00')];
    expect(sortSlotsByTime(input).map(s => s.scheduleId)).toEqual([2, 1]);
  });
});
