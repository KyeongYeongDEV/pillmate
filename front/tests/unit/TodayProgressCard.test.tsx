import React from 'react';
import { render, screen } from '@testing-library/react-native';
import TodayProgressCard from '@/components/home/TodayProgressCard';

const progress = { taken: 4, total: 6, nextScheduleTime: '12:00', nextScheduleLabel: '점심약' };

describe('TodayProgressCard', () => {
  it('taken/total + 퍼센트를 표시한다', () => {
    render(<TodayProgressCard progress={progress} />);
    expect(screen.getByText('4/6')).toBeTruthy();
    expect(screen.getByText('67%')).toBeTruthy();
  });

  it('다음 복약 시간을 표시한다', () => {
    render(<TodayProgressCard progress={progress} />);
    expect(screen.getByText(/점심약/)).toBeTruthy();
    expect(screen.getByText(/12:00/)).toBeTruthy();
  });

  it('total=0 이면 0%', () => {
    render(<TodayProgressCard progress={{ taken: 0, total: 0, nextScheduleTime: null, nextScheduleLabel: null }} />);
    expect(screen.getByText('0%')).toBeTruthy();
  });

  it('모두 완료하면 완료 메시지', () => {
    render(<TodayProgressCard progress={{ taken: 3, total: 3, nextScheduleTime: null, nextScheduleLabel: null }} />);
    expect(screen.getByText(/모두 완료/)).toBeTruthy();
  });

  it('다음 복약이 없으면 표시 안 함', () => {
    render(<TodayProgressCard progress={{ taken: 2, total: 6, nextScheduleTime: null, nextScheduleLabel: null }} />);
    expect(screen.queryByText(/점심약/)).toBeNull();
  });
});
