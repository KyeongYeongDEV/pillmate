import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import DrugSearchScreen from '@/app/prescription/search';
import { useSearchDrugsQuery } from '@/store/slices/drugApi';
import { useLocalSearchParams, router } from 'expo-router';
import * as recentSearches from '@/lib/search/recentSearches';
import type { DrugSearchResult } from '@/types/prescription';

const DRUG: DrugSearchResult = {
  id: 1,
  kdCode: '670500700',
  name: '암로디핀정 5mg',
  ingredient: '암로디핀베실산염',
  efficacy: '본태성 고혈압',
  form: '정제',
  imageUrl: null,
};

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), back: jest.fn() },
  useLocalSearchParams: jest.fn(),
}));

jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn().mockResolvedValue(undefined),
  ImpactFeedbackStyle: { Light: 'LIGHT' },
}));

jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: React.ReactNode }) => children,
}));

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

jest.mock('@/lib/router/safeBack', () => ({ safeBack: jest.fn() }));

jest.mock('@/store/hooks', () => ({
  useAppDispatch: () => jest.fn(),
  useAppSelector: () => [],
}));

jest.mock('@/store/slices/drugApi', () => ({
  useSearchDrugsQuery: jest.fn(),
}));

jest.mock('@/components/search/RecentSearchChips', () => () => null);

jest.mock('@/lib/search/recentSearches', () => ({
  getRecentSearches: jest.fn().mockResolvedValue([]),
  saveRecentSearches: jest.fn().mockResolvedValue(undefined),
  clearRecentSearches: jest.fn().mockResolvedValue(undefined),
  pushRecentSearch: jest.requireActual('@/lib/search/recentSearches').pushRecentSearch,
}));

const mockParams = useLocalSearchParams as jest.Mock;
const mockSearch = useSearchDrugsQuery as jest.Mock;
const mockGetRecent = recentSearches.getRecentSearches as jest.Mock;
const mockSaveRecent = recentSearches.saveRecentSearches as jest.Mock;

describe('DrugSearchScreen — 조회전용 vs 추가가능 모드', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockSearch.mockReturnValue({ data: [DRUG], isFetching: false });
    mockGetRecent.mockResolvedValue([]);
  });

  it('mode 파라미터 없음(등록 허브 "약 검색하기") → + 추가 버튼 미노출, 상세 보기만', () => {
    mockParams.mockReturnValue({ q: '암로디핀' });
    render(<DrugSearchScreen />);

    expect(screen.getByText('암로디핀정 5mg')).toBeTruthy();
    expect(screen.queryByTestId(`add-btn-${DRUG.kdCode}`)).toBeNull();
    expect(screen.getByTestId(`detail-btn-${DRUG.kdCode}`)).toBeTruthy();
  });

  it('mode=add(review "검색으로 추가") → + 추가 버튼 노출', () => {
    mockParams.mockReturnValue({ q: '암로디핀', mode: 'add' });
    render(<DrugSearchScreen />);

    expect(screen.getByTestId(`add-btn-${DRUG.kdCode}`)).toBeTruthy();
  });
});

describe('DrugSearchScreen — 최근 검색 실제 기록/영속화', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockSearch.mockReturnValue({ data: [DRUG], isFetching: false });
    mockGetRecent.mockResolvedValue([]);
  });

  it('마운트 시 저장된 최근 검색어를 로드한다', async () => {
    mockParams.mockReturnValue({});
    render(<DrugSearchScreen />);
    await waitFor(() => expect(mockGetRecent).toHaveBeenCalled());
  });

  it('상세보기(카드 탭) 시점에 유효 검색어로 기록 — 중간 타이핑은 기록 안 함', async () => {
    mockParams.mockReturnValue({ q: '암로디핀' });
    render(<DrugSearchScreen />);

    // debounce 이후 리렌더까지 대기 — 아직 상세보기 안 눌렀으니 기록 없어야 함
    await waitFor(() => expect(screen.getByTestId(`detail-btn-${DRUG.kdCode}`)).toBeTruthy());
    expect(mockSaveRecent).not.toHaveBeenCalled();

    await act(async () => {
      fireEvent.press(screen.getByTestId(`detail-btn-${DRUG.kdCode}`));
    });

    expect(mockSaveRecent).toHaveBeenCalledWith(['암로디핀']);
    expect(router.push).toHaveBeenCalledWith(`/drug/${DRUG.kdCode}`);
  });

  it('추가 모드에서 + 버튼 탭 시에도 검색어 기록', async () => {
    mockParams.mockReturnValue({ q: '암로디핀', mode: 'add' });
    render(<DrugSearchScreen />);

    await act(async () => {
      fireEvent.press(screen.getByTestId(`add-btn-${DRUG.kdCode}`));
    });

    expect(mockSaveRecent).toHaveBeenCalledWith(['암로디핀']);
  });
});
