const mockGetPerm = jest.fn();
const mockGetItem = jest.fn();
const mockSetItem = jest.fn();

jest.mock('expo-notifications', () => ({
  getPermissionsAsync: () => mockGetPerm(),
}));

jest.mock('expo-secure-store', () => ({
  getItemAsync: (...args: any[]) => mockGetItem(...args),
  setItemAsync: (...args: any[]) => mockSetItem(...args),
}));

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

import React from 'react';
import { Linking } from 'react-native';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import PushPermissionBanner from '@/components/home/PushPermissionBanner';

const BANNER_TITLE = '복약 알림을 받을 수 없어요';

describe('PushPermissionBanner', () => {
  let openSettings: jest.SpyInstance;

  beforeEach(() => {
    mockGetPerm.mockReset();
    mockGetItem.mockReset().mockResolvedValue(null);
    mockSetItem.mockReset().mockResolvedValue(undefined);
    openSettings = jest.spyOn(Linking, 'openSettings').mockResolvedValue(undefined);
  });

  afterEach(() => openSettings.mockRestore());

  it('권한 denied 면 안내 배너 노출', async () => {
    mockGetPerm.mockResolvedValue({ status: 'denied' });
    render(<PushPermissionBanner />);
    expect(await screen.findByText(BANNER_TITLE)).toBeTruthy();
    expect(screen.getByLabelText('설정 열기')).toBeTruthy();
  });

  it('권한 granted 면 배너 숨김', async () => {
    mockGetPerm.mockResolvedValue({ status: 'granted' });
    render(<PushPermissionBanner />);
    await waitFor(() => expect(mockGetPerm).toHaveBeenCalled());
    expect(screen.queryByText(BANNER_TITLE)).toBeNull();
  });

  it('권한 조회 실패해도 배너 숨김 (오탐 안내 금지)', async () => {
    mockGetPerm.mockRejectedValue(new Error('no native module'));
    render(<PushPermissionBanner />);
    await waitFor(() => expect(mockGetPerm).toHaveBeenCalled());
    expect(screen.queryByText(BANNER_TITLE)).toBeNull();
  });

  it('"설정 열기" 누르면 Linking.openSettings 호출', async () => {
    mockGetPerm.mockResolvedValue({ status: 'denied' });
    render(<PushPermissionBanner />);
    fireEvent.press(await screen.findByLabelText('설정 열기'));
    expect(openSettings).toHaveBeenCalledTimes(1);
  });

  it('"나중에" 누르면 배너가 사라지고 dismiss 시각을 저장', async () => {
    mockGetPerm.mockResolvedValue({ status: 'denied' });
    render(<PushPermissionBanner />);
    fireEvent.press(await screen.findByLabelText('나중에'));

    expect(screen.queryByText(BANNER_TITLE)).toBeNull();
    await waitFor(() =>
      expect(mockSetItem).toHaveBeenCalledWith('pillmate_push_guide_dismissed_at', expect.any(String)));
  });

  it('24시간 안에 닫은 이력이 있으면 denied 여도 미노출', async () => {
    mockGetPerm.mockResolvedValue({ status: 'denied' });
    mockGetItem.mockResolvedValue(String(Date.now()));
    render(<PushPermissionBanner />);
    await waitFor(() => expect(mockGetItem).toHaveBeenCalled());
    expect(screen.queryByText(BANNER_TITLE)).toBeNull();
  });

  it('닫은 지 24시간 넘었으면 재노출', async () => {
    mockGetPerm.mockResolvedValue({ status: 'denied' });
    mockGetItem.mockResolvedValue(String(Date.now() - 25 * 60 * 60 * 1000));
    render(<PushPermissionBanner />);
    expect(await screen.findByText(BANNER_TITLE)).toBeTruthy();
  });
});
