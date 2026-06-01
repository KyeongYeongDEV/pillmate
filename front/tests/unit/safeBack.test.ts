const mockBack = jest.fn();
const mockReplace = jest.fn();
const mockCanGoBack = jest.fn();

jest.mock('expo-router', () => ({
  router: {
    back: (...args: any[]) => mockBack(...args),
    replace: (...args: any[]) => mockReplace(...args),
    canGoBack: () => mockCanGoBack(),
  },
}));

import { safeBack } from '@/lib/router/safeBack';

describe('safeBack', () => {
  beforeEach(() => {
    mockBack.mockReset();
    mockReplace.mockReset();
    mockCanGoBack.mockReset();
  });

  it('stack 있음 → router.back() 호출, replace 미호출', () => {
    mockCanGoBack.mockReturnValue(true);
    safeBack('/(tabs)/home');
    expect(mockBack).toHaveBeenCalledTimes(1);
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('stack 없음 → router.replace(fallback) 호출, back 미호출', () => {
    mockCanGoBack.mockReturnValue(false);
    safeBack('/(tabs)/group');
    expect(mockReplace).toHaveBeenCalledWith('/(tabs)/group');
    expect(mockBack).not.toHaveBeenCalled();
  });

  it('fallback 생략 → 기본값 /(tabs)/home 로 replace', () => {
    mockCanGoBack.mockReturnValue(false);
    safeBack();
    expect(mockReplace).toHaveBeenCalledWith('/(tabs)/home');
  });

  it('탭 root 같은 위치에서 호출 — back 시도 안 함 (canGoBack=false 분기)', () => {
    mockCanGoBack.mockReturnValue(false);
    safeBack('/(tabs)/prescriptions');
    expect(mockBack).not.toHaveBeenCalled();
    expect(mockReplace).toHaveBeenCalledWith('/(tabs)/prescriptions');
  });
});
