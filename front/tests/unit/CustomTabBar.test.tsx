import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import CustomTabBar from '@/components/navigation/CustomTabBar';

// expo-haptics mock
jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn(),
  ImpactFeedbackStyle: { Light: 'LIGHT', Medium: 'MEDIUM' },
}));

// expo-router mock
jest.mock('expo-router', () => ({
  router: { push: jest.fn() },
}));

// react-native-safe-area-context mock
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ bottom: 34, top: 0, left: 0, right: 0 }),
}));

const makeProps = (activeRouteName = 'home') => ({
  state: {
    index: ['home', 'drugs', 'register-fab', 'groups', 'my'].indexOf(activeRouteName),
    routes: [
      { key: 'home-1', name: 'home' },
      { key: 'drugs-1', name: 'drugs' },
      { key: 'register-fab-1', name: 'register-fab' },
      { key: 'groups-1', name: 'groups' },
      { key: 'my-1', name: 'my' },
    ],
    routeNames: ['home', 'drugs', 'register-fab', 'groups', 'my'],
    type: 'tab',
    key: 'tab-1',
    stale: false as const,
    history: [],
  } as any,
  descriptors: {} as any,
  navigation: { navigate: jest.fn() } as any,
  insets: { bottom: 34, top: 0, left: 0, right: 0 },
});

describe('CustomTabBar', () => {
  it('4개 탭 레이블(register-fab 제외) 렌더', () => {
    render(<CustomTabBar {...makeProps()} />);
    expect(screen.getByLabelText('홈')).toBeTruthy();
    expect(screen.getByLabelText('약')).toBeTruthy();
    expect(screen.getByLabelText('그룹')).toBeTruthy();
    expect(screen.getByLabelText('MY')).toBeTruthy();
  });

  it('FAB 버튼 렌더', () => {
    render(<CustomTabBar {...makeProps()} />);
    expect(screen.getByLabelText('처방전 등록')).toBeTruthy();
  });

  it('FAB 탭 → router.push 호출', async () => {
    const { router } = require('expo-router');
    render(<CustomTabBar {...makeProps()} />);
    fireEvent.press(screen.getByLabelText('처방전 등록'));
    // Haptics is async; push call happens after
    await Promise.resolve();
    expect(router.push).toHaveBeenCalledWith('/prescription');
  });

  it('비활성 탭 탭 → navigation.navigate 호출', async () => {
    const props = makeProps('home'); // home이 active
    render(<CustomTabBar {...props} />);
    fireEvent.press(screen.getByLabelText('약'));
    // handleTabPress is async (Haptics); wait for it
    await Promise.resolve();
    await Promise.resolve();
    expect(props.navigation.navigate).toHaveBeenCalledWith('drugs');
  });

  it('활성 탭 다시 탭해도 navigate 안 함', async () => {
    const props = makeProps('home');
    render(<CustomTabBar {...props} />);
    fireEvent.press(screen.getByLabelText('홈'));
    await Promise.resolve();
    expect(props.navigation.navigate).not.toHaveBeenCalled();
  });
});
