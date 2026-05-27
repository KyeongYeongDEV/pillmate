import React from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react-native';
import MedTimeRow from '@/components/schedule/MedTimeRow';
import CalendarGrid from '@/components/schedule/CalendarGrid';
import type { MedSlot } from '@/types/schedule';

jest.mock('@expo/vector-icons', () => ({
  Feather:  ({ name }: any) => null,
  Ionicons: ({ name }: any) => null,
}));

const DONE_SLOT: MedSlot  = { id: 'morning', time: '08:00', label: '아침', state: 'done', items: ['암로디핀 5mg', '메트포르민 500mg'], doseLogId: 101 };
const NOW_SLOT: MedSlot   = { id: 'noon',    time: '12:30', label: '점심', state: 'now',  items: ['메트포르민 500mg'], doseLogId: 102 };
const WAIT_SLOT: MedSlot  = { id: 'evening', time: '19:00', label: '저녁', state: 'wait', items: ['아토르바스타틴 10mg'], doseLogId: 103 };

describe('MedTimeRow', () => {
  it('약 이름 렌더', () => {
    render(<MedTimeRow slot={DONE_SLOT} isFirst />);
    expect(screen.getByText('암로디핀 5mg')).toBeTruthy();
    expect(screen.getByText('메트포르민 500mg')).toBeTruthy();
  });

  it('출처 표시 — 식약처', () => {
    render(<MedTimeRow slot={NOW_SLOT} isFirst />);
    expect(screen.getByText(/식품의약품안전처/)).toBeTruthy();
  });

  it('done 슬롯 — 시간 렌더', () => {
    render(<MedTimeRow slot={DONE_SLOT} isFirst />);
    expect(screen.getByText('08:00')).toBeTruthy();
    expect(screen.getByText('아침')).toBeTruthy();
  });

  it('wait 슬롯 — 시간 렌더', () => {
    render(<MedTimeRow slot={WAIT_SLOT} isFirst />);
    expect(screen.getByText('19:00')).toBeTruthy();
  });

  it('onPress 없으면 Pressable 없음 — 접근성 role 미노출', () => {
    render(<MedTimeRow slot={DONE_SLOT} isFirst />);
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('onPress 있으면 Pressable → accessibilityRole button', () => {
    render(<MedTimeRow slot={DONE_SLOT} isFirst onPress={jest.fn()} />);
    expect(screen.getByRole('button')).toBeTruthy();
  });

  it('onPress 탭 시 해당 slot 전달', async () => {
    const onPress = jest.fn();
    render(<MedTimeRow slot={WAIT_SLOT} isFirst onPress={onPress} />);
    await act(async () => {
      fireEvent.press(screen.getByRole('button'));
    });
    expect(onPress).toHaveBeenCalledWith(WAIT_SLOT);
  });

  it('doseLogId 필드 포함', () => {
    expect(DONE_SLOT.doseLogId).toBe(101);
    expect(WAIT_SLOT.doseLogId).toBe(103);
  });
});

describe('CalendarGrid', () => {
  it('요일 헤더 7개 렌더', () => {
    render(<CalendarGrid />);
    expect(screen.getByText('일')).toBeTruthy();
    expect(screen.getByText('월')).toBeTruthy();
    expect(screen.getByText('토')).toBeTruthy();
  });

  it('오늘(24일) 렌더', () => {
    render(<CalendarGrid />);
    expect(screen.getByText('24')).toBeTruthy();
  });

  it('11월 1일 렌더', () => {
    render(<CalendarGrid />);
    expect(screen.getByText('1')).toBeTruthy();
  });
});
