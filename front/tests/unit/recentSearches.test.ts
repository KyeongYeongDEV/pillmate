import * as SecureStore from 'expo-secure-store';
import {
  getRecentSearches, saveRecentSearches, clearRecentSearches, pushRecentSearch,
  RECENT_SEARCHES_MAX,
} from '@/lib/search/recentSearches';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

const mockGet = SecureStore.getItemAsync as jest.Mock;
const mockSet = SecureStore.setItemAsync as jest.Mock;
const mockDelete = SecureStore.deleteItemAsync as jest.Mock;

describe('pushRecentSearch — 순수 함수', () => {
  it('신규 검색어를 맨 앞에 추가', () => {
    expect(pushRecentSearch(['오메가-3'], '메트포르민')).toEqual(['메트포르민', '오메가-3']);
  });

  it('중복 검색어는 기존 것 제거 후 맨 앞으로', () => {
    expect(pushRecentSearch(['메트포르민', '오메가-3'], '메트포르민')).toEqual(['메트포르민', '오메가-3']);
  });

  it(`최대 ${RECENT_SEARCHES_MAX}개로 cap`, () => {
    const full = Array.from({ length: RECENT_SEARCHES_MAX }, (_, i) => `약${i}`);
    const result = pushRecentSearch(full, '새약');
    expect(result).toHaveLength(RECENT_SEARCHES_MAX);
    expect(result[0]).toBe('새약');
    expect(result).not.toContain(`약${RECENT_SEARCHES_MAX - 1}`);
  });

  it('공백만 있는 검색어는 무시(기존 배열 그대로)', () => {
    expect(pushRecentSearch(['메트포르민'], '   ')).toEqual(['메트포르민']);
  });

  it('앞뒤 공백은 trim 되어 저장', () => {
    expect(pushRecentSearch([], '  타이레놀  ')).toEqual(['타이레놀']);
  });
});

describe('getRecentSearches / saveRecentSearches / clearRecentSearches', () => {
  beforeEach(() => jest.clearAllMocks());

  it('저장값 없으면 빈 배열', async () => {
    mockGet.mockResolvedValue(null);
    expect(await getRecentSearches()).toEqual([]);
  });

  it('저장된 JSON 배열 파싱해 반환', async () => {
    mockGet.mockResolvedValue(JSON.stringify(['메트포르민', '오메가-3']));
    expect(await getRecentSearches()).toEqual(['메트포르민', '오메가-3']);
  });

  it('손상된 JSON → 빈 배열(크래시 방지)', async () => {
    mockGet.mockResolvedValue('{invalid');
    expect(await getRecentSearches()).toEqual([]);
  });

  it('saveRecentSearches — JSON.stringify 하여 SecureStore 에 저장(8개 cap)', async () => {
    const many = Array.from({ length: 10 }, (_, i) => `약${i}`);
    await saveRecentSearches(many);
    expect(mockSet).toHaveBeenCalledWith(
      'pillmate_recent_searches',
      JSON.stringify(many.slice(0, RECENT_SEARCHES_MAX)),
    );
  });

  it('clearRecentSearches — SecureStore 키 삭제', async () => {
    await clearRecentSearches();
    expect(mockDelete).toHaveBeenCalledWith('pillmate_recent_searches');
  });
});
