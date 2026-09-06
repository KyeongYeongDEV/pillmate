import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { useLocalSearchParams } from 'expo-router';
import MemberScheduleScreen from '@/app/(tabs)/group/[id]/member/[userId]';
import {
  useGetMemberDayScheduleQuery,
  useGetMemberMonthAdherenceQuery,
} from '@/store/slices/scheduleApi';
import { useGetGroupDetailQuery } from '@/store/slices/caregroupApi';
import type { MedSlot } from '@/types/schedule';

jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: React.ReactNode }) => children,
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));

jest.mock('expo-router', () => ({
  useLocalSearchParams: jest.fn(),
  router: { push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => false },
}));

jest.mock('@expo/vector-icons', () => ({ Feather: () => null, Ionicons: () => null }));

jest.mock('@/store/slices/scheduleApi', () => ({
  useGetMemberDayScheduleQuery: jest.fn(),
  useGetMemberMonthAdherenceQuery: jest.fn(),
}));

jest.mock('@/store/slices/caregroupApi', () => ({ useGetGroupDetailQuery: jest.fn() }));

const mockParams = useLocalSearchParams as unknown as jest.Mock;
const mockDayQuery = useGetMemberDayScheduleQuery as unknown as jest.Mock;
const mockMonthQuery = useGetMemberMonthAdherenceQuery as unknown as jest.Mock;
const mockGroupDetail = useGetGroupDetailQuery as unknown as jest.Mock;

const SLOT: MedSlot = {
  id: 'morning', time: '08:00', label: '아침', state: 'wait',
  items: ['암로디핀 5mg'], doseLogId: 11,
};

// L2(알약 정보) 공유 권한이 없을 때 서버가 내려주는 마스킹 슬롯 형태.
const MASKED_SLOT: MedSlot = {
  id: 'morning', time: '08:00', label: '아침', state: 'done',
  items: [], drugCount: 2, prescriptionName: '약 정보 비공개', doseLogId: 11,
};

function setup(options: {
  params?: Record<string, string>;
  slots?: MedSlot[];
  dayError?: unknown;
  monthError?: unknown;
  dayLoading?: boolean;
  monthLoading?: boolean;
  dayData?: unknown;
  monthData?: unknown;
} = {}) {
  const hasDayData = !('dayData' in options);
  const hasMonthData = !('monthData' in options);
  mockParams.mockReturnValue(options.params ?? { id: '3', userId: '7', name: '박순자' });
  mockDayQuery.mockReturnValue({
    data: hasDayData
      ? { date: '2026-08-20', totalCount: 0, doneCount: 0, slots: options.slots ?? [] }
      : options.dayData,
    error: options.dayError,
    isLoading: options.dayLoading ?? false,
    isFetching: options.dayLoading ?? false,
    refetch: jest.fn(),
  });
  mockMonthQuery.mockReturnValue({
    data: hasMonthData ? {} : options.monthData,
    error: options.monthError,
    isLoading: options.monthLoading ?? false,
    isFetching: options.monthLoading ?? false,
    refetch: jest.fn(),
  });
  mockGroupDetail.mockReturnValue({
    data: { groupId: 3, name: '할머니 댁', memberCount: 2, members: [{ userId: 7, name: '박순자', role: 'PATIENT' }], inviteCode: null, recentActivities: [] },
  });
}

describe('구성원 복약 캘린더 화면', () => {
  beforeEach(() => jest.clearAllMocks());

  it('헤더에 누구의 복약인지 표시한다', () => {
    setup();
    render(<MemberScheduleScreen />);
    expect(screen.getByText('박순자님의 복약')).toBeTruthy();
  });

  it('name 파라미터가 없으면 그룹 상세의 구성원 이름을 쓴다', () => {
    setup({ params: { id: '3', userId: '7' } });
    render(<MemberScheduleScreen />);
    expect(screen.getByText('박순자님의 복약')).toBeTruthy();
  });

  it('patientId 로 일별/월별 조회를 호출한다', () => {
    setup();
    render(<MemberScheduleScreen />);
    expect(mockDayQuery).toHaveBeenCalledWith(
      expect.objectContaining({ patientId: 7 }),
      expect.anything(),
    );
    expect(mockMonthQuery).toHaveBeenCalledWith(
      expect.objectContaining({ patientId: 7 }),
      expect.anything(),
    );
  });

  // groupId 를 안 보내면 서버가 fail-closed 로 무조건 마스킹한다 — 권한 있어도 약 이름이 안 보이게 되는 회귀 방지.
  it('라우트의 groupId(id 파라미터)를 일별/월별 조회에 함께 보낸다', () => {
    setup({ params: { id: '3', userId: '7', name: '박순자' } });
    render(<MemberScheduleScreen />);
    expect(mockDayQuery).toHaveBeenCalledWith(
      expect.objectContaining({ patientId: 7, groupId: 3 }),
      expect.anything(),
    );
    expect(mockMonthQuery).toHaveBeenCalledWith(
      expect.objectContaining({ patientId: 7, groupId: 3 }),
      expect.anything(),
    );
  });

  it('복약 체크 버튼이 없다 — 남의 기록은 읽기 전용', () => {
    setup({ slots: [SLOT] });
    render(<MemberScheduleScreen />);
    expect(screen.getByText('암로디핀 5mg')).toBeTruthy();
    expect(screen.queryByLabelText('아침 08:00 복약 체크')).toBeNull();
  });

  it('읽기 전용 안내 문구를 보여 준다', () => {
    setup({ slots: [SLOT] });
    render(<MemberScheduleScreen />);
    expect(screen.getByText('보기 전용이에요. 복약 체크는 본인만 할 수 있어요')).toBeTruthy();
  });

  it('403 이면 권한 안내만 보여 주고 캘린더를 그리지 않는다', () => {
    setup({ slots: [SLOT], dayError: { status: 403, data: { error: { code: 'GROUP_ACCESS_DENIED' } } } });
    render(<MemberScheduleScreen />);
    expect(screen.getByText('이 구성원의 복약 정보를 볼 수 없어요')).toBeTruthy();
    expect(screen.queryByLabelText('연월 선택')).toBeNull();
  });

  it('월별 조회만 403 이어도 권한 안내를 보여 준다', () => {
    setup({ monthError: { status: 403 } });
    render(<MemberScheduleScreen />);
    expect(screen.getByText('이 구성원의 복약 정보를 볼 수 없어요')).toBeTruthy();
  });

  it('403 이 아닌 오류는 일반 실패 안내', () => {
    setup({ dayError: { status: 500 } });
    render(<MemberScheduleScreen />);
    expect(screen.getByText('복약 정보를 불러올 수 없어요')).toBeTruthy();
  });

  it('L2 마스킹 슬롯은 약 이름 대신 "N개 · 약 정보 비공개"를 보여주고 복약 완료 카운트(L1)는 그대로 반영한다', () => {
    setup({ slots: [MASKED_SLOT] });
    render(<MemberScheduleScreen />);
    expect(screen.getByText('2개 · 약 정보 비공개')).toBeTruthy();
    expect(screen.queryByText('암로디핀 5mg')).toBeNull();
    expect(screen.getByText('복약 1 / 1 완료')).toBeTruthy();
  });

  it('해당 날짜에 복약이 없으면 빈 안내', () => {
    setup({ slots: [] });
    render(<MemberScheduleScreen />);
    expect(screen.getByText('이 날짜에 등록된 복약이 없어요')).toBeTruthy();
  });

  it('첫 로딩(데이터 없음) 중엔 "0/0"·"없어요" 확정 문구를 띄우지 않는다', () => {
    setup({ dayData: undefined, dayLoading: true });
    render(<MemberScheduleScreen />);
    expect(screen.queryByText('이 날짜에 등록된 복약이 없어요')).toBeNull();
    expect(screen.queryByText('복약 0 / 0 완료')).toBeNull();
  });

  it('월간 데이터가 아직 없고 로딩 중이면 확정 요약을 띄우지 않는다', () => {
    setup({ monthData: undefined, monthLoading: true });
    render(<MemberScheduleScreen />);
    expect(screen.queryByText('이 날짜에 등록된 복약이 없어요')).toBeNull();
    expect(screen.queryByText('복약 0 / 0 완료')).toBeNull();
  });

  it('월간 조회만 실패해도 조용히 회색으로 렌더하지 않고 실패 안내를 보여 준다', () => {
    setup({ monthError: { status: 500 } });
    render(<MemberScheduleScreen />);
    expect(screen.getByText('복약 정보를 불러올 수 없어요')).toBeTruthy();
    expect(screen.queryByLabelText('연월 선택')).toBeNull();
  });

  it('로드 실패 안내에는 재시도 버튼이 있다', () => {
    setup({ dayError: { status: 500 } });
    render(<MemberScheduleScreen />);
    expect(screen.getByText('재시도')).toBeTruthy();
  });
});
