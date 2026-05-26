import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import TimeSlotCards, { TimeSlot } from '@/components/home/TimeSlotCards';

jest.mock('@expo/vector-icons', () => ({
  Feather:  ({ name }: any) => null,
  Ionicons: ({ name }: any) => null,
}));

const slots: TimeSlot[] = [
  { id: 'morning', label: '아침',    time: '8:00',  state: 'done', drugCount: 2, pillColors: ['#A8D4FF', '#FFAA6B'] },
  { id: 'noon',    label: '점심',    time: '12:30', state: 'now',  drugCount: 3, pillColors: ['#FFB3C1'] },
  { id: 'evening', label: '저녁',    time: '19:00', state: 'wait', drugCount: 2, pillColors: ['#C4B5FD'] },
  { id: 'bedtime', label: '취침 전', time: '22:00', state: 'wait', drugCount: 1, pillColors: ['#0066FF'] },
];

describe('TimeSlotCards', () => {
  it('4개 시간대 카드 렌더', () => {
    render(<TimeSlotCards slots={slots} />);
    expect(screen.getByText(/아침/)).toBeTruthy();
    expect(screen.getByText(/점심/)).toBeTruthy();
    expect(screen.getByText(/저녁/)).toBeTruthy();
    expect(screen.getByText(/취침 전/)).toBeTruthy();
  });

  it('done 상태 — 복용 완료 표시', () => {
    render(<TimeSlotCards slots={slots} />);
    expect(screen.getByText('복용 완료')).toBeTruthy();
  });

  it('now 상태 — 복용 중이에요 표시', () => {
    render(<TimeSlotCards slots={slots} />);
    expect(screen.getByText('복용 중이에요')).toBeTruthy();
  });

  it('wait 상태 — N개 예정 표시', () => {
    render(<TimeSlotCards slots={slots} />);
    const waitItems = screen.getAllByText(/개 예정/);
    expect(waitItems.length).toBe(2);
  });

  it('슬롯 탭 시 onSlotPress 호출', () => {
    const onSlotPress = jest.fn();
    render(<TimeSlotCards slots={slots} onSlotPress={onSlotPress} />);
    fireEvent.press(screen.getByText('복용 완료'));
    expect(onSlotPress).toHaveBeenCalledWith(slots[0]);
  });
});
