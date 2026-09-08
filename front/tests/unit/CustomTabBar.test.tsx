import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import CustomTabBar from '@/components/navigation/CustomTabBar';
import { router } from 'expo-router';

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
  navigation: {
    navigate: jest.fn(),
    emit: jest.fn(() => ({ defaultPrevented: false })),
  } as any,
  insets: { bottom: 34, top: 0, left: 0, right: 0 },
});

describe('CustomTabBar', () => {
  beforeEach(() => (router.push as jest.Mock).mockClear());

  it('4개 탭(홈/복약/약봉투/그룹) 렌더, 상담(chat) 제외', () => {
    render(<CustomTabBar {...makeProps()} />);
    expect(screen.getByLabelText('홈')).toBeTruthy();
    expect(screen.getByLabelText('복약')).toBeTruthy();
    expect(screen.getByLabelText('약봉투')).toBeTruthy();
    expect(screen.getByLabelText('그룹')).toBeTruthy();
    expect(screen.queryByLabelText('상담')).toBeNull();
  });

  it('비활성 탭(복약) → navigation.navigate(schedule)', async () => {
    const props = makeProps('home');
    render(<CustomTabBar {...props} />);
    fireEvent.press(screen.getByLabelText('복약'));
    await Promise.resolve();
    await Promise.resolve();
    expect(props.navigation.navigate).toHaveBeenCalledWith('schedule');
  });

  it('약봉투(register-fab) 탭 → router.push(/prescriptions), navigate 안 함', async () => {
    const props = makeProps('home');
    render(<CustomTabBar {...props} />);
    fireEvent.press(screen.getByLabelText('약봉투'));
    await Promise.resolve();
    await Promise.resolve();
    expect(router.push).toHaveBeenCalledWith('/prescriptions');
    expect(props.navigation.navigate).not.toHaveBeenCalled();
  });

  it('활성 탭(홈) 다시 탭해도 navigate 안 함', async () => {
    const props = makeProps('home');
    render(<CustomTabBar {...props} />);
    fireEvent.press(screen.getByLabelText('홈'));
    await Promise.resolve();
    expect(props.navigation.navigate).not.toHaveBeenCalled();
  });

  // Tabs.Screen 의 listeners.tabPress 는 탭바가 tabPress 를 emit 해야만 실행된다.
  // 이게 빠져서 그룹 탭이 목록으로 reset 되지 않고 상세 화면이 복원되던 회귀를 막는다.
  it('탭을 누르면 해당 route.key 로 tabPress 를 emit 한다', async () => {
    const props = makeProps('home');
    render(<CustomTabBar {...props} />);
    fireEvent.press(screen.getByLabelText('그룹'));
    await Promise.resolve();
    await Promise.resolve();
    expect(props.navigation.emit).toHaveBeenCalledWith({
      type: 'tabPress',
      target: 'group-4',
      canPreventDefault: true,
    });
  });

  it('활성 탭(그룹)을 다시 눌러도 tabPress 는 emit 된다 (목록으로 reset 되도록)', async () => {
    const props = makeProps('group');
    render(<CustomTabBar {...props} />);
    fireEvent.press(screen.getByLabelText('그룹'));
    await Promise.resolve();
    await Promise.resolve();
    expect(props.navigation.emit).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'tabPress', target: 'group-4' }),
    );
    expect(props.navigation.navigate).not.toHaveBeenCalled();
  });

  it('리스너가 defaultPrevented 하면 navigate 하지 않는다', async () => {
    const props = makeProps('home');
    (props.navigation.emit as jest.Mock).mockReturnValue({ defaultPrevented: true });
    render(<CustomTabBar {...props} />);
    fireEvent.press(screen.getByLabelText('복약'));
    await Promise.resolve();
    await Promise.resolve();
    expect(props.navigation.navigate).not.toHaveBeenCalled();
  });
});
