import React from 'react';
import { render, screen } from '@testing-library/react-native';
import TimeSlotCards, { TimeSlot } from '@/components/home/TimeSlotCards';

const slots: TimeSlot[] = [
  { id: 'morning', label: '아침', time: '08:00', drugCount: 2, status: 'done' },
  { id: 'noon', label: '점심', time: '12:00', drugCount: 3, status: 'current' },
  { id: 'evening', label: '저녁', time: '19:00', drugCount: 2, status: 'pending' },
  { id: 'bedtime', label: '취침', time: '22:00', drugCount: 1, status: 'pending' },
];

describe('TimeSlotCards', () => {
  it('4개 시간대 카드 렌더', () => {
    render(<TimeSlotCards slots={slots} />);
    expect(screen.getByText('아침')).toBeTruthy();
    expect(screen.getByText('점심')).toBeTruthy();
    expect(screen.getByText('저녁')).toBeTruthy();
    expect(screen.getByText('취침')).toBeTruthy();
  });

  it('완료 상태 레이블', () => {
    render(<TimeSlotCards slots={slots} />);
    expect(screen.getByText('완료')).toBeTruthy();
  });

  it('현재 상태 레이블', () => {
    render(<TimeSlotCards slots={slots} />);
    expect(screen.getByText('복용시간')).toBeTruthy();
  });

  it('대기 상태 레이블', () => {
    render(<TimeSlotCards slots={slots} />);
    const pendingLabels = screen.getAllByText('대기');
    expect(pendingLabels.length).toBe(2);
  });

  it('약 개수 표시', () => {
    render(<TimeSlotCards slots={slots} />);
    expect(screen.getByText('💊 3개')).toBeTruthy();
  });
});
