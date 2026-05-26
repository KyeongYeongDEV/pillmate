import React from 'react';
import { render, screen } from '@testing-library/react-native';
import MedTimeRow from '@/components/schedule/MedTimeRow';
import CalendarGrid from '@/components/schedule/CalendarGrid';
import type { MedSlot } from '@/types/schedule';

jest.mock('@expo/vector-icons', () => ({
  Feather:  ({ name }: any) => null,
  Ionicons: ({ name }: any) => null,
}));

const DONE_SLOT: MedSlot  = { id: 'morning', time: '08:00', label: '아침', state: 'done', items: ['암로디핀 5mg', '메트포르민 500mg'] };
const NOW_SLOT: MedSlot   = { id: 'noon',    time: '12:30', label: '점심', state: 'now',  items: ['메트포르민 500mg'] };
const WAIT_SLOT: MedSlot  = { id: 'evening', time: '19:00', label: '저녁', state: 'wait', items: ['아토르바스타틴 10mg'] };

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
