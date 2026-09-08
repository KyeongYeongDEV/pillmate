import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';
import GroupScheduleCalendar from '@/components/group/GroupScheduleCalendar';
import { useGetGroupMonthScheduleQuery } from '@/store/slices/caregroupApi';
import { getCurrentUserId } from '@/lib/auth/storage';
import { getKstToday, toMonthString } from '@/utils/calendarUtils';
import type { MemberView } from '@/types/caregroup';

jest.mock('expo-router', () => ({ router: { push: jest.fn() } }));
jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));
jest.mock('@/lib/auth/storage', () => ({ getCurrentUserId: jest.fn() }));
jest.mock('@/store/slices/caregroupApi', () => ({
  useGetGroupMonthScheduleQuery: jest.fn(),
}));

const mockQuery = useGetGroupMonthScheduleQuery as jest.Mock;
const mockGetCurrentUserId = getCurrentUserId as jest.Mock;
const mockPush = router.push as jest.Mock;

const MEMBERS: MemberView[] = [
  { userId: 7, name: '박순자', role: 'PATIENT' },
  { userId: 2, name: '김보호', role: 'GUARDIAN' },
];

function swatchColor(userId: number): string | undefined {
  const style = screen.getByTestId(`member-swatch-${userId}`).props.style;
  const flat = Array.isArray(style) ? Object.assign({}, ...style) : style;
  return flat.backgroundColor;
}

describe('GroupScheduleCalendar', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockGetCurrentUserId.mockReset().mockResolvedValue(999); // 아무도 본인 아님 → 모두 Pressable
    mockQuery.mockReset().mockReturnValue({ data: {}, error: undefined, refetch: jest.fn() });
  });

  it('멤버마다 서로 다른 고유색 스와치가 렌더된다', async () => {
    render(<GroupScheduleCalendar groupId={3} members={MEMBERS} />);
    await waitFor(() => expect(mockGetCurrentUserId).toHaveBeenCalled());
    expect(swatchColor(7)).toBeTruthy();
    expect(swatchColor(2)).toBeTruthy();
    expect(swatchColor(7)).not.toBe(swatchColor(2));
  });

  it('범례에는 색상 스와치만 있고 구성원 이름 텍스트는 표시하지 않는다', async () => {
    render(<GroupScheduleCalendar groupId={3} members={MEMBERS} />);
    await waitFor(() => expect(mockGetCurrentUserId).toHaveBeenCalled());
    expect(screen.queryByText('박순자')).toBeNull();
    expect(screen.queryByText('김보호')).toBeNull();
  });

  it('다음 달 버튼을 누르면 다음 month 로 쿼리한다', async () => {
    render(<GroupScheduleCalendar groupId={3} members={MEMBERS} />);
    await waitFor(() => expect(mockGetCurrentUserId).toHaveBeenCalled());
    const initialMonth = mockQuery.mock.calls[0][0].month;

    fireEvent.press(screen.getByLabelText('다음 달'));

    const months = mockQuery.mock.calls.map((c) => c[0].month);
    expect(months.some((m) => m !== initialMonth)).toBe(true);
  });

  it('멤버 범례를 탭하면 그 멤버의 복약 화면으로 이동한다', async () => {
    render(<GroupScheduleCalendar groupId={3} members={MEMBERS} />);
    await waitFor(() => expect(mockGetCurrentUserId).toHaveBeenCalled());

    fireEvent.press(screen.getByLabelText('박순자 복약 보기'));

    expect(mockPush).toHaveBeenCalledWith(expect.stringContaining('/group/3/member/7'));
  });

  it('날짜 칸 전체를 누르면 그 날짜의 그룹 복약 화면으로 이동한다', async () => {
    const [y, m] = getKstToday().split('-').map(Number);
    const dateStr = `${toMonthString(y, m)}-01`;
    mockQuery.mockReturnValue({
      data: { [dateStr]: [{ userId: 7, adherence: 'FULL' }] },
      error: undefined,
      refetch: jest.fn(),
    });

    render(<GroupScheduleCalendar groupId={3} members={MEMBERS} />);
    await waitFor(() => expect(mockGetCurrentUserId).toHaveBeenCalled());

    fireEvent.press(screen.getByLabelText('1일 그룹 복약 보기'));

    const [url] = mockPush.mock.calls[0];
    expect(url).toBe(`/group/3/day/${dateStr}`);
  });

  it('복약 기록이 없는 빈 날짜 칸도 눌러서 이동할 수 있다', async () => {
    const [y, m] = getKstToday().split('-').map(Number);
    const dateStr = `${toMonthString(y, m)}-02`;
    mockQuery.mockReturnValue({ data: {}, error: undefined, refetch: jest.fn() });

    render(<GroupScheduleCalendar groupId={3} members={MEMBERS} />);
    await waitFor(() => expect(mockGetCurrentUserId).toHaveBeenCalled());

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
