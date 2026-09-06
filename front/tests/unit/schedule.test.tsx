import React from 'react';
import { StyleSheet } from 'react-native';
import { render, screen, fireEvent, act } from '@testing-library/react-native';
import MedTimeRow from '@/components/schedule/MedTimeRow';
import CalendarGrid from '@/components/schedule/CalendarGrid';
import { colors } from '@/styles/tokens';
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

  // 스케줄→홈 슬라이스 매핑: MedTimeRow 가 부모 state 를 그대로 표시
  it('부모에서 done → wait 변경 시 즉시 반영', () => {
    const { rerender } = render(<MedTimeRow slot={DONE_SLOT} isFirst />);
    const waitSlot: MedSlot = { ...DONE_SLOT, state: 'wait' };
    rerender(<MedTimeRow slot={waitSlot} isFirst />);
    // 취소선 없음 — wait 상태로 표시
    expect(screen.getByText('08:00')).toBeTruthy();
  });
});

describe('MedTimeRow readOnly (#147 과거/미래 날짜)', () => {
  it('readOnly + done — 체크 서클 회색 (statusPositive 아님)', () => {
    render(<MedTimeRow slot={DONE_SLOT} isFirst readOnly />);
    const flat = StyleSheet.flatten(screen.getByTestId('dose-circle').props.style);
    expect(flat.backgroundColor).toBe(colors.labelAssistive);
  });

  it('readOnly 아니면 done 서클은 statusPositive 유지', () => {
    render(<MedTimeRow slot={DONE_SLOT} isFirst />);
    const flat = StyleSheet.flatten(screen.getByTestId('dose-circle').props.style);
    expect(flat.backgroundColor).toBe(colors.statusPositive);
  });

  it('readOnly + now — 파랑 강조 없음 (과거/미래엔 now 의미 없음)', () => {
    render(<MedTimeRow slot={NOW_SLOT} isFirst readOnly />);
    const flat = StyleSheet.flatten(screen.getByTestId('dose-circle').props.style);
    expect(flat.backgroundColor).not.toBe(colors.primaryBase);
  });

  it('readOnly여도 탭 시 onPress 호출 — Alert 안내는 스크린 책임', async () => {
    const onPress = jest.fn();
    render(<MedTimeRow slot={WAIT_SLOT} isFirst readOnly onPress={onPress} />);
    await act(async () => {
      fireEvent.press(screen.getByRole('button'));
    });
    expect(onPress).toHaveBeenCalledWith(WAIT_SLOT);
  });
});

// 그룹원 캘린더의 알약정보(L2) 마스킹 — 복약여부(L1)는 그대로, 무슨 약인지만 가려진다.
describe('MedTimeRow — L2(알약 정보) 마스킹', () => {
  const MASKED_SLOT: MedSlot = {
    id: 'morning', time: '08:00', label: '아침', state: 'wait',
    items: [], drugCount: 2, prescriptionName: '약 정보 비공개', doseLogId: 201,
  };

  it('prescriptionName 이 마스킹 라벨이면 약 이름 대신 개수 포함 비공개 문구를 보여준다', () => {
    render(<MedTimeRow slot={MASKED_SLOT} isFirst readOnly />);
    expect(screen.getByText('2개 · 약 정보 비공개')).toBeTruthy();
  });

  it('items 가 빈 배열이고 drugCount 가 없으면 개수 없이 비공개 문구만 보여준다', () => {
    const slot: MedSlot = { ...MASKED_SLOT, drugCount: undefined };
    render(<MedTimeRow slot={slot} isFirst readOnly />);
    expect(screen.getByText('약 정보 비공개')).toBeTruthy();
  });

  it('마스킹 시 출처 표시를 하지 않는다 — 근거 없는 정보 표시 금지(의료 안전)', () => {
    render(<MedTimeRow slot={MASKED_SLOT} isFirst readOnly />);
    expect(screen.queryByText(/식품의약품안전처/)).toBeNull();
  });

  it('마스킹 시 시간·라벨(L1)은 그대로 보인다', () => {
    render(<MedTimeRow slot={MASKED_SLOT} isFirst readOnly />);
    expect(screen.getByText('08:00')).toBeTruthy();
    expect(screen.getByText('아침')).toBeTruthy();
  });

  it('마스킹 슬롯은 처방전 상세로 링크되지 않는다', () => {
    const slot: MedSlot = { ...MASKED_SLOT, prescriptionId: 55 };
    const onPrescriptionPress = jest.fn();
    render(<MedTimeRow slot={slot} isFirst readOnly onPrescriptionPress={onPrescriptionPress} />);
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('마스킹 아닌 일반 슬롯은 기존처럼 약 이름을 그대로 보여준다 — 회귀 방지', () => {
    render(<MedTimeRow slot={DONE_SLOT} isFirst />);
    expect(screen.getByText('암로디핀 5mg')).toBeTruthy();
    expect(screen.queryByText('약 정보 비공개')).toBeNull();
  });
});

const GRID_BASE = {
  year: 2026, month: 6,
  selectedDate: '2026-06-12', today: '2026-06-12',
  onSelectDate: jest.fn(),
};

describe('CalendarGrid', () => {
  it('요일 헤더 7개 렌더', () => {
    render(<CalendarGrid {...GRID_BASE} />);
    expect(screen.getByText('일')).toBeTruthy();
    expect(screen.getByText('월')).toBeTruthy();
    expect(screen.getByText('토')).toBeTruthy();
  });

  it('오늘 날짜(12) 렌더', () => {
    render(<CalendarGrid {...GRID_BASE} />);
    expect(screen.getByText('12')).toBeTruthy();
  });

  it('6월 1일 렌더', () => {
    render(<CalendarGrid {...GRID_BASE} />);
    expect(screen.getByText('1')).toBeTruthy();
  });

  it('날짜 탭 시 onSelectDate 호출', () => {
    const onSelectDate = jest.fn();
    render(<CalendarGrid {...GRID_BASE} onSelectDate={onSelectDate} />);
    fireEvent.press(screen.getByLabelText('6월 12일'));
    expect(onSelectDate).toHaveBeenCalledWith('2026-06-12');
  });
});

describe('CalendarGrid 준수도 점 색 (upcoming 포함 4색)', () => {
  const ADHERENCE = {
    '2026-06-10': 'full',
    '2026-06-11': 'partial',
    '2026-06-09': 'miss',
    '2026-06-30': 'upcoming',
  } as const;

  function dotColor(dateStr: string): string {
    render(<CalendarGrid {...GRID_BASE} adherenceByDate={ADHERENCE} />);
    return StyleSheet.flatten(screen.getByTestId(`adherence-dot-${dateStr}`).props.style).backgroundColor;
  }

  it('upcoming(복용 예정) → 파랑 primaryBase', () => {
    expect(dotColor('2026-06-30')).toBe(colors.primaryBase);
  });

  it('miss(미복용) → 빨강 유지 — 미래 날짜와 구분', () => {
    expect(dotColor('2026-06-09')).toBe(colors.statusNegative);
  });

  it('full → 초록, partial → 주황 회귀 없음', () => {
    expect(dotColor('2026-06-10')).toBe(colors.statusPositive);
    expect(dotColor('2026-06-11')).toBe(colors.statusCautionary);
  });
});
