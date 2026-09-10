import React from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react-native';
import { useLocalSearchParams } from 'expo-router';
import ShareSettingsScreen from '@/app/(tabs)/group/[id]/share-settings';
import {
  useGetShareSettingsQuery,
  useUpdateMemberShareMutation,
  useUpdatePrescriptionShareMutation,
  useUpdateMyColorMutation,
} from '@/store/slices/caregroupApi';
import type { ShareSettingsView } from '@/store/slices/caregroupApi';
import { SELECTABLE_COLOR_PALETTE } from '@/utils/memberColors';

jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: React.ReactNode }) => children,
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));

jest.mock('expo-router', () => ({
  useLocalSearchParams: jest.fn(),
  router: { push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => false },
}));

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

jest.mock('@/store/slices/caregroupApi', () => ({
  useGetShareSettingsQuery: jest.fn(),
  useUpdateMemberShareMutation: jest.fn(),
  useUpdatePrescriptionShareMutation: jest.fn(),
  useUpdateMyColorMutation: jest.fn(),
}));

const mockParams = useLocalSearchParams as unknown as jest.Mock;
const mockQuery = useGetShareSettingsQuery as unknown as jest.Mock;
const mockMemberMutation = useUpdateMemberShareMutation as unknown as jest.Mock;
const mockPrescriptionMutation = useUpdatePrescriptionShareMutation as unknown as jest.Mock;
const mockColorMutation = useUpdateMyColorMutation as unknown as jest.Mock;

const view = (over: Partial<ShareSettingsView> = {}): ShareSettingsView => ({
  members: [
    { userId: 7, name: '박순자', role: 'PATIENT', shared: false },
    { userId: 2, name: '김철수', role: 'GUARDIAN', shared: true },
  ],
  prescriptions: [
    { prescriptionId: 2, label: '감기약', prescribedAt: '2026-09-01', shared: false, status: 'ONGOING' },
  ],
  myColor: null,
  ...over,
});

let memberMutate: jest.Mock;
let prescriptionMutate: jest.Mock;
let colorMutate: jest.Mock;

function setup(options: {
  data?: ShareSettingsView;
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
} = {}) {
  mockParams.mockReturnValue({ id: '3' });
  mockQuery.mockReturnValue({
    data: options.data ?? { members: [], prescriptions: [], myColor: null },
    isLoading: options.isLoading ?? false,
    isError: options.isError ?? false,
    error: options.error,
    refetch: jest.fn(),
  });
  memberMutate = jest.fn(() => ({ unwrap: () => Promise.resolve() }));
  prescriptionMutate = jest.fn(() => ({ unwrap: () => Promise.resolve() }));
  colorMutate = jest.fn(() => ({ unwrap: () => Promise.resolve() }));
  mockMemberMutation.mockReturnValue([memberMutate, {}]);
  mockPrescriptionMutation.mockReturnValue([prescriptionMutate, {}]);
  mockColorMutation.mockReturnValue([colorMutate, {}]);
}

describe('알약 정보 공유 설정 화면 — 구성원 + 약봉투 2섹션', () => {
  beforeEach(() => jest.clearAllMocks());

  it('구성원 섹션과 약봉투 섹션 헤더를 모두 렌더한다', () => {
    setup({ data: view() });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('공유할 구성원')).toBeTruthy();
    expect(screen.getByText('공유할 약봉투')).toBeTruthy();
  });

  it('구성원을 이름만으로 렌더한다 (역할 표시 없음)', () => {
    setup({ data: view() });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('박순자')).toBeTruthy();
    expect(screen.getByText('김철수')).toBeTruthy();
    expect(screen.queryByText('환자')).toBeNull();
    expect(screen.queryByText('보호자')).toBeNull();
  });

  it('약봉투를 라벨·날짜와 함께 렌더한다', () => {
    setup({ data: view() });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('감기약')).toBeTruthy();
    expect(screen.getByText('2026.09.01')).toBeTruthy();
  });

  it('약봉투 라벨이 없으면 약봉투 번호로 폴백 표시한다', () => {
    setup({ data: view({ prescriptions: [{ prescriptionId: 9, label: null, prescribedAt: '2026-09-01', shared: false, status: 'ONGOING' }] }) });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('약봉투 #9')).toBeTruthy();
  });

  it('구성원 토글을 켜면 updateMemberShare 뮤테이션을 호출한다', async () => {
    setup({ data: view() });
    render(<ShareSettingsScreen />);
    await act(async () => {
      fireEvent(screen.getByLabelText('박순자에게 공유'), 'valueChange', true);
    });
    expect(memberMutate).toHaveBeenCalledWith({ groupId: 3, viewerUserId: 7, enabled: true });
    expect(prescriptionMutate).not.toHaveBeenCalled();
  });

  it('약봉투 토글을 켜면 updatePrescriptionShare 뮤테이션을 호출한다', async () => {
    setup({ data: view() });
    render(<ShareSettingsScreen />);
    await act(async () => {
      fireEvent(screen.getByLabelText('감기약 그룹에 공유'), 'valueChange', true);
    });
    expect(prescriptionMutate).toHaveBeenCalledWith({ groupId: 3, prescriptionId: 2, enabled: true });
    expect(memberMutate).not.toHaveBeenCalled();
  });

  it('구성원이 비면 섹션별 빈 상태 안내를 보여 준다', () => {
    setup({ data: view({ members: [] }) });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('함께하는 구성원이 없어요')).toBeTruthy();
  });

  it('약봉투가 비면 섹션별 빈 상태 안내를 보여 준다', () => {
    setup({ data: view({ prescriptions: [] }) });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('현재 복용중인 약봉투가 없어요')).toBeTruthy();
  });

  it('로딩 중이면 목록/빈안내를 확정 표시하지 않는다', () => {
    setup({ isLoading: true });
    render(<ShareSettingsScreen />);
    expect(screen.queryByText('공유할 구성원')).toBeNull();
    expect(screen.queryByText('함께하는 구성원이 없어요')).toBeNull();
  });

  it('403 이면 접근 불가 안내를 보여 준다', () => {
    setup({ isError: true, error: { status: 403 } });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('이 그룹의 공유 설정에 접근할 수 없어요')).toBeTruthy();
  });

  it('내 색상 섹션에 선택 가능한 색 스와치 12개를 렌더한다', () => {
    setup({ data: view() });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('내 색상')).toBeTruthy();
    expect(SELECTABLE_COLOR_PALETTE).toHaveLength(12);
    SELECTABLE_COLOR_PALETTE.forEach((color) => {
      expect(screen.getByLabelText(`색상 ${color}`)).toBeTruthy();
    });
  });

  it('현재 myColor 와 일치하는 스와치만 선택 표시된다', () => {
    const myColor = SELECTABLE_COLOR_PALETTE[3];
    setup({ data: view({ myColor }) });
    render(<ShareSettingsScreen />);
    expect(screen.getByLabelText(`색상 ${myColor}`).props.accessibilityState?.selected).toBe(true);
    expect(screen.getByLabelText(`색상 ${SELECTABLE_COLOR_PALETTE[0]}`).props.accessibilityState?.selected).toBe(false);
  });

  it('myColor 가 null 이면 어떤 스와치도 선택 표시되지 않는다', () => {
    setup({ data: view({ myColor: null }) });
    render(<ShareSettingsScreen />);
    SELECTABLE_COLOR_PALETTE.forEach((color) => {
      expect(screen.getByLabelText(`색상 ${color}`).props.accessibilityState?.selected).toBe(false);
    });
  });

  it('스와치를 탭하면 그 색으로 updateMyColor 를 호출한다', async () => {
    const target = SELECTABLE_COLOR_PALETTE[5];
    setup({ data: view() });
    render(<ShareSettingsScreen />);
    await act(async () => {
      fireEvent.press(screen.getByLabelText(`색상 ${target}`));
    });
    expect(colorMutate).toHaveBeenCalledWith(target);
  });
});
