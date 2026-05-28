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

  // 내부 stateMap 제거 검증: 컴포넌트는 stateless — 탭 후 UI 변화 없음 (부모 책임)
  it('wait 슬롯 탭 → onSlotPress 에 현재 slot 전달 (상태 변경 없음)', async () => {
    const onSlotPress = jest.fn();
    render(<TimeSlotCards slots={slots} onSlotPress={onSlotPress} />);

    const waitItems = screen.getAllByText('복용 대기');
    await act(async () => {
      fireEvent.press(waitItems[0]);
    });

    // 컴포넌트 자체는 상태 미변경 — 여전히 '복용 대기' 2개
    expect(screen.getAllByText('복용 대기').length).toBe(2);
    // 콜백은 현재 slot(wait 상태)으로 호출됨 — 부모가 toggle 결정
    expect(onSlotPress).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'evening', state: 'wait' }),
    );
  });

  it('done 슬롯 탭 → onSlotPress 에 현재 slot 전달 (상태 변경 없음)', async () => {
    const onSlotPress = jest.fn();
    render(<TimeSlotCards slots={slots} onSlotPress={onSlotPress} />);

    const doneItem = screen.getByText('복용 완료');
    await act(async () => {
      fireEvent.press(doneItem);
    });

    // 컴포넌트 자체는 상태 미변경 — '복용 완료' 여전히 1개
    expect(screen.getAllByText('복용 완료').length).toBe(1);
    expect(onSlotPress).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'morning', state: 'done' }),
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

  it('부모에서 props 변경 시 UI 즉시 반영 (controlled)', () => {
    const { rerender } = render(<TimeSlotCards slots={slots} />);
    expect(screen.getAllByText('복용 대기').length).toBe(2);

    // 부모가 evening을 done으로 변경
    const updatedSlots = slots.map(s =>
      s.id === 'evening' ? { ...s, state: 'done' as const } : s,
    );
    rerender(<TimeSlotCards slots={updatedSlots} />);
    expect(screen.getAllByText('복용 완료').length).toBe(2);
  });
});
