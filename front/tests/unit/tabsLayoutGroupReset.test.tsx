import React from 'react';
import { render } from '@testing-library/react-native';

// Tabs/Tabs.Screen 을 가짜로 대체해 각 Screen 에 넘어간 props(listeners)를 캡처한다.
const screenProps: any[] = [];

jest.mock('expo-router', () => {
  const MockReact = require('react');
  const Tabs = ({ children }: any) => MockReact.createElement(MockReact.Fragment, null, children);
  Tabs.Screen = (props: any) => {
    screenProps.push(props);
    return null;
  };
  return { Tabs };
});

jest.mock('@/components/navigation/CustomTabBar', () => () => null);

import TabsLayout from '@/app/(tabs)/_layout';

function groupListeners() {
  const group = screenProps.find((p) => p.name === 'group');
  const navigation = { navigate: jest.fn() };
  return { listeners: group.listeners({ navigation }), navigation };
}

describe('(tabs)/_layout — 그룹 탭은 항상 목록으로', () => {
  beforeEach(() => {
    screenProps.length = 0;
    render(<TabsLayout />);
  });

  it('그룹 탭에 tabPress 리스너가 등록되어 있다', () => {
    const { listeners } = groupListeners();
    expect(typeof listeners.tabPress).toBe('function');
  });

  it('tabPress 시 목록(index)으로 이동시킨다', () => {
    const { listeners, navigation } = groupListeners();
    listeners.tabPress({ preventDefault: jest.fn() });
    expect(navigation.navigate).toHaveBeenCalledWith('group', { screen: 'index' });
  });

  // 기본 동작(navigate('group'))을 막지 않으면 마지막 스택 상태(상세)가 다시 복원돼
  // 목록과 상세가 번갈아 나온다 — 그 회귀를 고정한다.
  it('tabPress 시 기본 동작을 preventDefault 한다', () => {
    const { listeners } = groupListeners();
    const event = { preventDefault: jest.fn() };
    listeners.tabPress(event);
    expect(event.preventDefault).toHaveBeenCalled();
  });
});
