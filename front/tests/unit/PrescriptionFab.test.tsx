import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import PrescriptionFab from '@/components/navigation/PrescriptionFab';

jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn(),
  ImpactFeedbackStyle: { Medium: 'MEDIUM' },
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn() },
  useSegments: () => ['(tabs)', 'home'],
}));

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ bottom: 34, top: 0, left: 0, right: 0 }),
}));

describe('PrescriptionFab', () => {
  it('처방전 등록 버튼 렌더', () => {
    render(<PrescriptionFab />);
    expect(screen.getByLabelText('처방전 등록')).toBeTruthy();
  });

  it('탭 → /prescription 으로 이동', async () => {
    const { router } = require('expo-router');
    render(<PrescriptionFab />);
    fireEvent.press(screen.getByLabelText('처방전 등록'));
    await Promise.resolve();
    await Promise.resolve();
    expect(router.push).toHaveBeenCalledWith('/prescription');
  });

  it('그룹 탭 → null 렌더 (단일 FAB 정책)', () => {
    jest.resetModules();
    jest.doMock('expo-router', () => ({
      router: { push: jest.fn() },
      useSegments: () => ['(tabs)', 'group'],
    }));
    const PrescriptionFabGroup = require('@/components/navigation/PrescriptionFab').default;
    const { toJSON } = render(<PrescriptionFabGroup />);
    expect(toJSON()).toBeNull();
  });
});
