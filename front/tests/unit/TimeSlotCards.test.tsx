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
  { id: 'morning', label: '아침',    time: '8:00',  state: 'done', drugCount: 2, pillColors: ['#A8D4FF', '#FFAA6B'], doseLogIds: [4] },
  { id: 'noon',    label: '점심',    time: '12:30', state: 'now',  drugCount: 3, pillColors: ['#FFB3C1'], doseLogIds: [5] },
  { id: 'evening', label: '저녁',    time: '19:00', state: 'wait', drugCount: 2, pillColors: ['#C4B5FD'], doseLogIds: [6] },
  { id: 'bedtime', label: '취침 전', time: '22:00', state: 'wait', drugCount: 1, pillColors: ['#0066FF'], doseLogIds: [7] },
];

describe('TimeSlotCards', () => {
  it('4개 시간대 카드 렌더 (시간 표시)', () => {
    render(<TimeSlotCards slots={slots} />);
    expect(screen.getByText('8:00')).toBeTruthy();
    expect(screen.getByText('12:30')).toBeTruthy();
    expect(screen.getByText('19:00')).toBeTruthy();
    expect(screen.getByText('22:00')).toBeTruthy();
  });

  it('done 상태 — 복용 완료 fallback 표시', () => {
    render(<TimeSlotCards slots={slots} />);
    expect(screen.getByText('복용 완료')).toBeTruthy();
  });

  it('now 상태 — 지금 드세요 fallback 표시', () => {
    render(<TimeSlotCards slots={slots} />);
    expect(screen.getByText('지금 드세요')).toBeTruthy();
  });

  it('wait 상태 — 복용 대기 fallback 표시', () => {
    render(<TimeSlotCards slots={slots} />);
    const waitItems = screen.getAllByText('복용 대기');
    expect(waitItems.length).toBe(2);
  });

  it('처방전명이 있으면 처방전명 표시', () => {
    const withPresc: TimeSlot[] = [
      { ...slots[0], prescriptionId: 1, prescriptionName: '내과 처방전' },
    ];
    render(<TimeSlotCards slots={withPresc} />);
    expect(screen.getByText('내과 처방전')).toBeTruthy();
  });

  // 체크박스 탭 → onSlotPress 호출 (상태 변경은 부모 책임)
  it('wait 슬롯 체크박스 탭 → onSlotPress 에 현재 slot 전달', async () => {
    const onSlotPress = jest.fn();
    render(<TimeSlotCards slots={slots} onSlotPress={onSlotPress} />);

    const checkboxes = screen.getAllByRole('checkbox');
    // slots 순서: done(morning=0), now(noon=1), wait(evening=2), wait(bedtime=3)
    await act(async () => {
      fireEvent.press(checkboxes[2]);
    });

    expect(screen.getAllByText('복용 대기').length).toBe(2);
    expect(onSlotPress).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'evening', state: 'wait' }),
    );
  });

  it('done 슬롯 체크박스 탭 → onSlotPress 에 현재 slot 전달', async () => {
    const onSlotPress = jest.fn();
    render(<TimeSlotCards slots={slots} onSlotPress={onSlotPress} />);

    const checkboxes = screen.getAllByRole('checkbox');
    await act(async () => {
      fireEvent.press(checkboxes[0]);
    });

    expect(screen.getAllByText('복용 완료').length).toBe(1);
    expect(onSlotPress).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'morning', state: 'done' }),
    );
  });

  it('체크박스 탭 시 onSlotPress 호출', async () => {
    const onSlotPress = jest.fn();
    render(<TimeSlotCards slots={slots} onSlotPress={onSlotPress} />);
    await act(async () => {
      fireEvent.press(screen.getAllByRole('checkbox')[0]);
    });
    expect(onSlotPress).toHaveBeenCalled();
  });

  it('처방전명 탭 시 onPrescriptionPress 호출', async () => {
    const onPrescriptionPress = jest.fn();
    const withPresc: TimeSlot[] = [
      { ...slots[0], prescriptionId: 42, prescriptionName: '내과 처방전' },
    ];
    render(<TimeSlotCards slots={withPresc} onPrescriptionPress={onPrescriptionPress} />);
    await act(async () => {
      fireEvent.press(screen.getByRole('link'));
    });
    expect(onPrescriptionPress).toHaveBeenCalledWith(
      expect.objectContaining({ prescriptionId: 42 }),
    );
  });

  it('부모에서 props 변경 시 UI 즉시 반영 (controlled)', () => {
    const { rerender } = render(<TimeSlotCards slots={slots} />);
    expect(screen.getAllByText('복용 대기').length).toBe(2);

    const updatedSlots = slots.map(s =>
      s.id === 'evening' ? { ...s, state: 'done' as const } : s,
    );
    rerender(<TimeSlotCards slots={updatedSlots} />);
    expect(screen.getAllByText('복용 완료').length).toBe(2);
  });
});
