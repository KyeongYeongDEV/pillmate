import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import CustomTabBar from '@/components/navigation/CustomTabBar';

jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn(),
  ImpactFeedbackStyle: { Light: 'LIGHT', Medium: 'MEDIUM' },
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn() },
}));

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ bottom: 34, top: 0, left: 0, right: 0 }),
}));

const ROUTE_NAMES = ['home', 'schedule', 'register-fab', 'chat', 'group'];

const makeProps = (activeRouteName = 'home') => ({
  state: {
    index: ROUTE_NAMES.indexOf(activeRouteName),
    routes: ROUTE_NAMES.map((name, i) => ({ key: `${name}-${i}`, name })),
    routeNames: ROUTE_NAMES,
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
    expect(screen.getByLabelText('복약')).toBeTruthy();
    expect(screen.getByLabelText('상담')).toBeTruthy();
    expect(screen.getByLabelText('그룹')).toBeTruthy();
  });

  it('비활성 탭 탭 → navigation.navigate 호출', async () => {
    const props = makeProps('home');
    render(<CustomTabBar {...props} />);
    fireEvent.press(screen.getByLabelText('복약'));
    await Promise.resolve();
    await Promise.resolve();
    expect(props.navigation.navigate).toHaveBeenCalledWith('schedule');
  });

  it('활성 탭 다시 탭해도 navigate 안 함', async () => {
    const props = makeProps('home');
    render(<CustomTabBar {...props} />);
    fireEvent.press(screen.getByLabelText('홈'));
    await Promise.resolve();
    expect(props.navigation.navigate).not.toHaveBeenCalled();
  });
});
