import React from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react-native';
import { useLocalSearchParams } from 'expo-router';
import ShareSettingsScreen from '@/app/(tabs)/group/[id]/share-settings';
import {
  useGetShareablePrescriptionsQuery,
  useUpdatePrescriptionShareMutation,
} from '@/store/slices/caregroupApi';
import type { ShareablePrescriptionView } from '@/store/slices/caregroupApi';

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
  useGetShareablePrescriptionsQuery: jest.fn(),
  useUpdatePrescriptionShareMutation: jest.fn(),
}));

const mockParams = useLocalSearchParams as unknown as jest.Mock;
const mockQuery = useGetShareablePrescriptionsQuery as unknown as jest.Mock;
const mockMutation = useUpdatePrescriptionShareMutation as unknown as jest.Mock;

const prescription = (over: Partial<ShareablePrescriptionView> = {}): ShareablePrescriptionView => ({
  prescriptionId: 2, label: '감기약', prescribedAt: '2026-09-01', shared: false, status: 'ONGOING', ...over,
});

let mutate: jest.Mock;

function setup(options: {
  data?: ShareablePrescriptionView[];
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
} = {}) {
  mockParams.mockReturnValue({ id: '3' });
  mockQuery.mockReturnValue({
    data: options.data ?? [],
    isLoading: options.isLoading ?? false,
    isError: options.isError ?? false,
    error: options.error,
    refetch: jest.fn(),
  });
  mutate = jest.fn(() => ({ unwrap: () => Promise.resolve() }));
  mockMutation.mockReturnValue([mutate, {}]);
}

describe('알약 정보 공유 설정 화면 — 약봉투별 토글', () => {
  beforeEach(() => jest.clearAllMocks());

  it('약봉투 목록을 라벨·날짜와 함께 렌더한다', () => {
    setup({ data: [prescription({ prescriptionId: 2, label: '감기약', prescribedAt: '2026-09-01' })] });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('감기약')).toBeTruthy();
    expect(screen.getByText('2026.09.01')).toBeTruthy();
  });

  it('라벨이 없으면 약봉투 번호로 폴백 표시한다', () => {
    setup({ data: [prescription({ prescriptionId: 9, label: null })] });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('약봉투 #9')).toBeTruthy();
  });

  it('토글을 켜면 updatePrescriptionShare 뮤테이션을 호출한다', async () => {
    setup({ data: [prescription({ prescriptionId: 2, shared: false })] });
    render(<ShareSettingsScreen />);
    await act(async () => {
      fireEvent(screen.getByLabelText('감기약 그룹에 공유'), 'valueChange', true);
    });
    expect(mutate).toHaveBeenCalledWith({ groupId: 3, prescriptionId: 2, enabled: true });
  });

  it('목록이 비면 빈 상태 안내를 보여 준다', () => {
    setup({ data: [] });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('현재 복용중인 약봉투가 없어요')).toBeTruthy();
  });

  it('로딩 중이면 목록/빈안내를 확정 표시하지 않는다', () => {
    setup({ isLoading: true });
    render(<ShareSettingsScreen />);
    expect(screen.queryByText('현재 복용중인 약봉투가 없어요')).toBeNull();
  });

  it('403 이면 접근 불가 안내를 보여 준다', () => {
    setup({ isError: true, error: { status: 403 } });
    render(<ShareSettingsScreen />);
    expect(screen.getByText('이 그룹의 공유 설정에 접근할 수 없어요')).toBeTruthy();
  });
});
