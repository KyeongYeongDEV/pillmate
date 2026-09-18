import React from 'react';
import { render } from '@testing-library/react-native';
import * as Notifications from 'expo-notifications';
import { router } from 'expo-router';
import NotificationsBootstrap from '@/lib/notifications/NotificationsBootstrap';
import { notificationApiSlice } from '@/store/slices/notificationApi';

const mockDispatch = jest.fn();
const mockRemove = jest.fn();
let responseListener: ((response: unknown) => void) | null = null;

jest.mock('expo-router', () => ({ router: { push: jest.fn() } }));
jest.mock('@/store/hooks', () => ({ useAppDispatch: () => mockDispatch }));
jest.mock('@/lib/auth/storage', () => ({ getToken: jest.fn(() => Promise.resolve(null)) }));
jest.mock('@/lib/auth/refreshSession', () => ({ refreshSessionIfNeeded: jest.fn() }));
jest.mock('@/lib/notifications/setup', () => ({
  configureNotificationHandler: jest.fn(),
  ensureAndroidNotificationChannels: jest.fn(() => Promise.resolve()),
}));
jest.mock('@/lib/notifications/pushRegistration', () => ({ registerPushForCurrentUser: jest.fn() }));
jest.mock('@/lib/notifications/pushSync', () => ({ handlePushReceived: jest.fn() }));

jest.mock('expo-notifications', () => ({
  addNotificationResponseReceivedListener: jest.fn((cb: (response: unknown) => void) => {
    responseListener = cb;
    return { remove: mockRemove };
  }),
  addNotificationReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
}));

// 사용자 요청(2026-09-18) — 푸시 탭으로 앱 진입 시 해당 알림을 읽음 처리해 배지 숫자가 줄어들게 함
describe('NotificationsBootstrap — 푸시 탭 시 읽음 처리', () => {
  beforeEach(() => {
    mockDispatch.mockClear();
    (router.push as jest.Mock).mockClear();
    responseListener = null;
  });

  it('notificationId 있으면 markRead dispatch + 라우트 이동 둘 다 한다', () => {
    const initiateSpy = jest.spyOn(notificationApiSlice.endpoints.markRead, 'initiate');
    render(<NotificationsBootstrap />);
    const response = {
      notification: { request: { content: { data: { notificationId: '42', route: '/group/7' } } } },
    };

    responseListener!(response);

    expect(initiateSpy).toHaveBeenCalledWith(42);
    expect(mockDispatch).toHaveBeenCalledWith(initiateSpy.mock.results[0].value);
    expect(router.push).toHaveBeenCalledWith('/group/7');
    initiateSpy.mockRestore();
  });

  it('notificationId 없으면 markRead dispatch 없이 라우트 이동만 한다', () => {
    render(<NotificationsBootstrap />);
    const response = {
      notification: { request: { content: { data: { route: '/home' } } } },
    };

    responseListener!(response);

    expect(mockDispatch).not.toHaveBeenCalled();
    expect(router.push).toHaveBeenCalledWith('/home');
  });
});
