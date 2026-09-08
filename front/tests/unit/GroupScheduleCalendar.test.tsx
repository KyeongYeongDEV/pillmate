import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import { router } from 'expo-router';
import GroupScheduleCalendar from '@/components/group/GroupScheduleCalendar';
import { useGetGroupMonthScheduleQuery } from '@/store/slices/caregroupApi';
import { getKstToday, toMonthString } from '@/utils/calendarUtils';
import type { MemberView } from '@/types/caregroup';

jest.mock('expo-router', () => ({ router: { push: jest.fn() } }));
jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));
jest.mock('@/store/slices/caregroupApi', () => ({
  useGetGroupMonthScheduleQuery: jest.fn(),
}));

const mockQuery = useGetGroupMonthScheduleQuery as jest.Mock;
const mockPush = router.push as jest.Mock;

const MEMBERS: MemberView[] = [
  { userId: 7, name: '박순자', role: 'PATIENT' },
  { userId: 2, name: '김보호', role: 'GUARDIAN' },
];

describe('GroupScheduleCalendar', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockQuery.mockReset().mockReturnValue({ data: {}, error: undefined, refetch: jest.fn() });
  });

  it('하단 범례 영역을 렌더하지 않는다 (색 스와치 없음)', () => {
    render(<GroupScheduleCalendar groupId={3} members={MEMBERS} />);
    expect(screen.queryByTestId('member-swatch-7')).toBeNull();
    expect(screen.queryByTestId('member-swatch-2')).toBeNull();
    expect(screen.queryByLabelText('박순자 복약 보기')).toBeNull();
  });

  it('다음 달 버튼을 누르면 다음 month 로 쿼리한다', () => {
    render(<GroupScheduleCalendar groupId={3} members={MEMBERS} />);
    const initialMonth = mockQuery.mock.calls[0][0].month;

    fireEvent.press(screen.getByLabelText('다음 달'));

    const months = mockQuery.mock.calls.map((c) => c[0].month);
    expect(months.some((m) => m !== initialMonth)).toBe(true);
  });

  it('날짜 칸 전체를 누르면 그 날짜의 그룹 복약 화면으로 이동한다', () => {
    const [y, m] = getKstToday().split('-').map(Number);
    const dateStr = `${toMonthString(y, m)}-01`;
    mockQuery.mockReturnValue({
      data: { [dateStr]: [{ userId: 7, adherence: 'FULL' }] },
      error: undefined,
      refetch: jest.fn(),
    });

    render(<GroupScheduleCalendar groupId={3} members={MEMBERS} />);

    fireEvent.press(screen.getByLabelText('1일 그룹 복약 보기'));

    const [url] = mockPush.mock.calls[0];
    expect(url).toBe(`/group/3/day/${dateStr}`);
  });

  it('복약 기록이 없는 빈 날짜 칸도 눌러서 이동할 수 있다', () => {
    const [y, m] = getKstToday().split('-').map(Number);
    const dateStr = `${toMonthString(y, m)}-02`;
    mockQuery.mockReturnValue({ data: {}, error: undefined, refetch: jest.fn() });

    render(<GroupScheduleCalendar groupId={3} members={MEMBERS} />);

    fireEvent.press(screen.getByLabelText('2일 그룹 복약 보기'));

    expect(mockPush).toHaveBeenCalledWith(`/group/3/day/${dateStr}`);
  });

  it('에러 시 안내문과 재시도 버튼을 보여준다', () => {
    const refetch = jest.fn();
    mockQuery.mockReturnValue({ data: undefined, error: { status: 403 }, refetch });
    render(<GroupScheduleCalendar groupId={3} members={MEMBERS} />);

    expect(screen.getByText('그룹 복약 현황을 불러올 수 없어요')).toBeTruthy();
    fireEvent.press(screen.getByLabelText('재시도'));
    expect(refetch).toHaveBeenCalled();
  });
});
