import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import { useLocalSearchParams } from 'expo-router';
import GroupDayScheduleScreen from '@/app/(tabs)/group/[id]/day/[date]';
import {
  useGetGroupDetailQuery,
  useGetGroupDayScheduleQuery,
} from '@/store/slices/caregroupApi';
import type { GroupMemberDayView } from '@/store/slices/caregroupApi';
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

jest.mock('@/store/slices/caregroupApi', () => ({
  useGetGroupDetailQuery: jest.fn(),
  useGetGroupDayScheduleQuery: jest.fn(),
}));

const mockParams = useLocalSearchParams as unknown as jest.Mock;
const mockDayQuery = useGetGroupDayScheduleQuery as unknown as jest.Mock;
const mockGroupDetail = useGetGroupDetailQuery as unknown as jest.Mock;

const SLOT_A: MedSlot = {
  id: 'morning', time: '08:00', label: '아침', state: 'done',
  items: ['암로디핀 5mg'], doseLogId: 11,
};
const MASKED_SLOT: MedSlot = {
  id: 'evening', time: '20:00', label: '저녁', state: 'wait',
  items: [], drugCount: 2, prescriptionName: '약 정보 비공개',
};
const SLOT_B: MedSlot = {
  id: 'noon', time: '12:00', label: '점심', state: 'done',
  items: ['메트포르민 500mg'], doseLogId: 22,
};

const MEMBERS: GroupMemberDayView[] = [
  {
    userId: 1, name: '테스트유저1',
    schedule: { date: '2026-09-07', totalCount: 2, doneCount: 1, slots: [SLOT_A, MASKED_SLOT] },
  },
  {
    userId: 2, name: '테스트유저2',
    schedule: { date: '2026-09-07', totalCount: 1, doneCount: 1, slots: [SLOT_B] },
  },
];

function dotColor(userId: number): string | undefined {
  const style = screen.getByTestId(`member-dot-${userId}`).props.style;
  const flat = Array.isArray(style) ? Object.assign({}, ...style) : style;
  return flat.backgroundColor;
}

function setup(options: {
  members?: GroupMemberDayView[];
  error?: unknown;
  isLoading?: boolean;
  data?: unknown;
} = {}) {
  const hasData = !('data' in options);
  mockParams.mockReturnValue({ id: '3', date: '2026-09-07' });
  mockDayQuery.mockReturnValue({
    data: hasData ? (options.members ?? MEMBERS) : options.data,
    error: options.error,
    isLoading: options.isLoading ?? false,
    isFetching: options.isLoading ?? false,
    refetch: jest.fn(),
  });
  mockGroupDetail.mockReturnValue({
    data: {
      groupId: 3, name: '할머니 댁', memberCount: 2,
      members: [
        { userId: 1, name: '테스트유저1', role: 'PATIENT' },
        { userId: 2, name: '테스트유저2', role: 'GUARDIAN' },
      ],
      inviteCode: null, recentActivities: [],
    },
  });
}

describe('그룹 날짜별 복약 화면', () => {
  beforeEach(() => jest.clearAllMocks());

  it('그날 그룹 구성원 전원의 섹션을 각자 이름과 함께 렌더한다', () => {
    setup();
    render(<GroupDayScheduleScreen />);
    expect(screen.getByText('테스트유저1')).toBeTruthy();
    expect(screen.getByText('테스트유저2')).toBeTruthy();
  });

  it('각 멤버 섹션에 그 멤버의 슬롯 약 이름(또는 마스킹 문구)이 보인다', () => {
    setup();
    render(<GroupDayScheduleScreen />);
    expect(screen.getByText('암로디핀 5mg')).toBeTruthy();
    expect(screen.getByText('2개 · 약 정보 비공개')).toBeTruthy();
    expect(screen.getByText('메트포르민 500mg')).toBeTruthy();
  });

  it('멤버 섹션마다 복약 완료 카운트를 그대로 반영한다', () => {
    setup();
    render(<GroupDayScheduleScreen />);
    expect(screen.getByText('복약 1 / 2 완료')).toBeTruthy();
    expect(screen.getByText('복약 1 / 1 완료')).toBeTruthy();
  });

  it('멤버별 색점이 서로 다르다', () => {
    setup();
    render(<GroupDayScheduleScreen />);
    expect(dotColor(1)).toBeTruthy();
    expect(dotColor(2)).toBeTruthy();
    expect(dotColor(1)).not.toBe(dotColor(2));
  });

  it('복약이 없는 멤버 섹션엔 빈 안내를 보여준다', () => {
    setup({
      members: [
        { userId: 1, name: '테스트유저1', schedule: { date: '2026-09-07', totalCount: 0, doneCount: 0, slots: [] } },
      ],
    });
    render(<GroupDayScheduleScreen />);
    expect(screen.getByText('이 날짜에 등록된 복약이 없어요')).toBeTruthy();
  });

  it('403 이면 권한 안내를 보여준다', () => {
    setup({ data: undefined, error: { status: 403 } });
    render(<GroupDayScheduleScreen />);
    expect(screen.getByText('이 날짜의 그룹 복약 정보를 볼 수 없어요')).toBeTruthy();
  });

  it('일반 오류 시 안내와 재시도 버튼을 보여준다', () => {
    setup({ data: undefined, error: { status: 500 } });
    render(<GroupDayScheduleScreen />);
    expect(screen.getByText('그룹 복약 정보를 불러올 수 없어요')).toBeTruthy();
    expect(screen.getByText('재시도')).toBeTruthy();
  });

  it('첫 로딩(데이터 없음) 중엔 "없어요"·"0/0" 확정 문구를 띄우지 않는다', () => {
    setup({ data: undefined, isLoading: true });
    render(<GroupDayScheduleScreen />);
    expect(screen.queryByText('이 날짜에 등록된 복약이 없어요')).toBeNull();
    expect(screen.queryByText('이 날짜에 그룹 구성원의 복약 정보가 없어요')).toBeNull();
    expect(screen.queryByText('복약 0 / 0 완료')).toBeNull();
  });
});
