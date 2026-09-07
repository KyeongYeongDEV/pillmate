import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { useLocalSearchParams } from 'expo-router';
import SharedPrescriptionScreen from '@/app/(tabs)/group/[id]/prescription/[prescriptionId]';
import {
  useGetGroupDetailQuery,
  useGetSharedPrescriptionQuery,
} from '@/store/slices/caregroupApi';
import type { SharedPrescriptionView } from '@/store/slices/caregroupApi';

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
  useGetSharedPrescriptionQuery: jest.fn(),
}));

const mockParams = useLocalSearchParams as unknown as jest.Mock;
const mockQuery = useGetSharedPrescriptionQuery as unknown as jest.Mock;
const mockGroupDetail = useGetGroupDetailQuery as unknown as jest.Mock;

const VIEW: SharedPrescriptionView = {
  id: 61,
  ownerUserId: 1,
  prescribedAt: '2026-09-01',
  label: '9월 정기약(고혈압)',
  status: 'ONGOING',
  periodStart: '2026-09-01',
  periodEnd: '2026-09-30',
  daysRemaining: 22,
  progressRate: 0.26,
  adherenceRate: 0.83,
  drugs: [
    {
      nameRaw: '노바스크정5mg(암로디핀베실산염)',
      matchedDrugName: '노바스크정5밀리그램',
      matchedKdCode: '197000037',
      doseAmount: 1,
      doseUnit: '정',
      frequency: 2,
      durationDays: 30,
      imageUrl: null,
      confidence: null,
    },
  ],
};

function setup(options: {
  params?: Record<string, string>;
  data?: unknown;
  error?: unknown;
  isLoading?: boolean;
} = {}) {
  const hasData = !('data' in options);
  mockParams.mockReturnValue(options.params ?? { id: '3', prescriptionId: '61', name: '박순자' });
  mockQuery.mockReturnValue({
    data: hasData ? VIEW : options.data,
    error: options.error,
    isLoading: options.isLoading ?? false,
    isFetching: options.isLoading ?? false,
    refetch: jest.fn(),
  });
  mockGroupDetail.mockReturnValue({
    data: {
      groupId: 3, name: '할머니 댁', memberCount: 2,
      members: [{ userId: 1, name: '박순자', role: 'PATIENT' }],
      inviteCode: null, recentActivities: [],
    },
  });
}

describe('그룹 공유 약봉투 화면', () => {
  beforeEach(() => jest.clearAllMocks());

  it('구성원 이름과 함께 약 목록을 렌더한다', () => {
    setup();
    render(<SharedPrescriptionScreen />);
    expect(screen.getByText('박순자님의 약봉투')).toBeTruthy();
    expect(screen.getByText('노바스크정5밀리그램')).toBeTruthy();
  });

  it('약봉투 라벨·복용 기간·순응도를 표시한다', () => {
    setup();
    render(<SharedPrescriptionScreen />);
    expect(screen.getByText('9월 정기약(고혈압)')).toBeTruthy();
    expect(screen.getByText(/9\.1 → 9\.30/)).toBeTruthy();
    expect(screen.getByText('D-22')).toBeTruthy();
    expect(screen.getByText('복용중')).toBeTruthy();
  });

  it('403 + PILL_046 이면 공유 안 함 안내를 보여주고 재시도 버튼은 없다', () => {
    setup({ data: undefined, error: { status: 403, data: { error: { code: 'PILL_046' } } } });
    render(<SharedPrescriptionScreen />);
    expect(screen.getByText('이 구성원이 약 정보를 공유하지 않았어요')).toBeTruthy();
    expect(screen.queryByText('다시 시도')).toBeNull();
  });

  it('일반 오류면 안내와 재시도 버튼을 보여준다', () => {
    setup({ data: undefined, error: { status: 500 } });
    render(<SharedPrescriptionScreen />);
    expect(screen.getByText('약 정보를 불러올 수 없어요')).toBeTruthy();
    expect(screen.getByText('다시 시도')).toBeTruthy();
  });

  it('첫 로딩 중엔 "없어요"·"0종" 확정 문구를 띄우지 않는다', () => {
    setup({ data: undefined, isLoading: true });
    render(<SharedPrescriptionScreen />);
    expect(screen.queryByText('등록된 약 정보가 없어요')).toBeNull();
    expect(screen.queryByText('약 0종')).toBeNull();
  });

  it('drugs 가 빈 배열이면 빈 안내를 보여준다', () => {
    setup({ data: { ...VIEW, drugs: [] } });
    render(<SharedPrescriptionScreen />);
    expect(screen.getByText('등록된 약 정보가 없어요')).toBeTruthy();
  });
});
