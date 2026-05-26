import React from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react-native';
import TimeSlotCards, { TimeSlot } from '@/components/home/TimeSlotCards';

jest.mock('@expo/vector-icons', () => ({
  Feather:  ({ name }: any) => null,
  Ionicons: ({ name }: any) => null,
}));

jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn(() => Promise.resolve()),
  ImpactFeedbackStyle: { Light: 'Light', Medium: 'Medium', Heavy: 'Heavy' },
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

  it('now 상태 — 지금 드세요 표시', () => {
    render(<TimeSlotCards slots={slots} />);
    expect(screen.getByText('지금 드세요')).toBeTruthy();
  });

  it('wait 상태 — 복용 대기 표시', () => {
    render(<TimeSlotCards slots={slots} />);
    const waitItems = screen.getAllByText('복용 대기');
    expect(waitItems.length).toBe(2);
  });

  it('wait 슬롯 탭 → done 으로 전환 (복용 완료 표시)', async () => {
    const onSlotPress = jest.fn();
    render(<TimeSlotCards slots={slots} onSlotPress={onSlotPress} />);

    // evening slot starts as 'wait' → shows '복용 대기'
    const waitItems = screen.getAllByText('복용 대기');
    await act(async () => {
      fireEvent.press(waitItems[0]);
    });

    // after toggle, one of the wait slots becomes done
    const doneItems = screen.getAllByText('복용 완료');
    expect(doneItems.length).toBe(2);
    expect(onSlotPress).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'evening', state: 'done' }),
    );
  });

  it('done 슬롯 탭 → wait 으로 전환 (복용 대기 표시)', async () => {
    const onSlotPress = jest.fn();
    render(<TimeSlotCards slots={slots} onSlotPress={onSlotPress} />);

    const doneItem = screen.getByText('복용 완료');
    await act(async () => {
      fireEvent.press(doneItem);
    });

    // morning was done; after toggle it becomes wait → '복용 대기' appears 3 times
    const waitItems = screen.getAllByText('복용 대기');
    expect(waitItems.length).toBe(3);
    expect(onSlotPress).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'morning', state: 'wait' }),
    );
  });

  it('슬롯 탭 시 onSlotPress 호출', async () => {
    const onSlotPress = jest.fn();
    render(<TimeSlotCards slots={slots} onSlotPress={onSlotPress} />);
    await act(async () => {
      fireEvent.press(screen.getByText('복용 완료'));
    });
    expect(onSlotPress).toHaveBeenCalled();
  });
});
