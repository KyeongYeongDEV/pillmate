const mockGetItem = jest.fn();
const mockSetItem = jest.fn();

jest.mock('expo-secure-store', () => ({
  getItemAsync: (...args: any[]) => mockGetItem(...args),
  setItemAsync: (...args: any[]) => mockSetItem(...args),
}));

import {
  PUSH_GUIDE_SNOOZE_MS,
  getPushGuideDismissedAt,
  savePushGuideDismissedAt,
  shouldShowPushGuide,
} from '@/lib/notifications/permissionGuide';

const NOW = 1_700_000_000_000;

describe('shouldShowPushGuide — 권한 상태별 노출', () => {
  it('granted 면 미노출', () => {
    expect(shouldShowPushGuide('granted', null, NOW)).toBe(false);
  });

  it('undetermined 면 미노출 (권한 요청 다이얼로그가 담당)', () => {
    expect(shouldShowPushGuide('undetermined', null, NOW)).toBe(false);
  });

  it('unknown(조회 실패) 면 미노출 — 오탐 안내 금지', () => {
    expect(shouldShowPushGuide('unknown', null, NOW)).toBe(false);
  });

  it('denied 이고 닫은 적 없으면 노출', () => {
    expect(shouldShowPushGuide('denied', null, NOW)).toBe(true);
  });
});

describe('shouldShowPushGuide — 빈도 제한(24시간)', () => {
  it('닫은 직후에는 미노출', () => {
    expect(shouldShowPushGuide('denied', NOW, NOW)).toBe(false);
  });

  it('24시간 직전에는 미노출', () => {
    expect(shouldShowPushGuide('denied', NOW - PUSH_GUIDE_SNOOZE_MS + 1, NOW)).toBe(false);
  });

  it('24시간 경과 시 재노출', () => {
    expect(shouldShowPushGuide('denied', NOW - PUSH_GUIDE_SNOOZE_MS, NOW)).toBe(true);
  });

  it('기기 시계가 되돌아가 dismissedAt 이 미래면 재노출 (영구 숨김 방지)', () => {
    expect(shouldShowPushGuide('denied', NOW + PUSH_GUIDE_SNOOZE_MS, NOW)).toBe(true);
  });
});

describe('permissionGuide 저장소', () => {
  beforeEach(() => {
    mockGetItem.mockReset();
    mockSetItem.mockReset();
  });

  it('getPushGuideDismissedAt — 저장값을 숫자로 반환', async () => {
    mockGetItem.mockResolvedValue(String(NOW));
    await expect(getPushGuideDismissedAt()).resolves.toBe(NOW);
  });

  it('getPushGuideDismissedAt — 미저장 시 null', async () => {
    mockGetItem.mockResolvedValue(null);
    await expect(getPushGuideDismissedAt()).resolves.toBeNull();
  });

  it('getPushGuideDismissedAt — 손상값/읽기 실패 시 null', async () => {
    mockGetItem.mockResolvedValue('corrupted');
    await expect(getPushGuideDismissedAt()).resolves.toBeNull();
    mockGetItem.mockRejectedValue(new Error('keystore'));
    await expect(getPushGuideDismissedAt()).resolves.toBeNull();
  });

  it('savePushGuideDismissedAt — 타임스탬프를 문자열로 저장', async () => {
    mockSetItem.mockResolvedValue(undefined);
    await savePushGuideDismissedAt(NOW);
    expect(mockSetItem).toHaveBeenCalledWith('pillmate_push_guide_dismissed_at', String(NOW));
  });

  it('savePushGuideDismissedAt — 저장 실패해도 throw X', async () => {
    mockSetItem.mockRejectedValue(new Error('keystore'));
    await expect(savePushGuideDismissedAt(NOW)).resolves.toBeUndefined();
  });
});
