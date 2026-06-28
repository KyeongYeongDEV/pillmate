import React from 'react';
import { render, screen } from '@testing-library/react-native';
import LoginScreen from '@/app/(auth)/login';

jest.mock('@/store/slices/authApi', () => ({
  useKakaoLoginMutation: () => [jest.fn(), { isLoading: false }],
  useExchangeKakaoCodeMutation: () => [jest.fn(), { isLoading: false }],
}));

jest.mock('expo-router', () => ({
  router: { replace: jest.fn() },
}));

jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: React.ReactNode }) => children,
}));

jest.mock('react-native-svg', () => ({
  __esModule: true,
  default: 'Svg',
  Svg: 'Svg',
  Path: 'Path',
}));

describe('LoginScreen 카카오 버튼', () => {
  it('공식 카피 "카카오톡으로 계속하기" 노출', () => {
    render(<LoginScreen />);
    expect(screen.getByText('카카오톡으로 계속하기')).toBeTruthy();
  });

  it('💬 이모지 제거 + 카카오톡 로고(KakaoTalkIcon) 노출', () => {
    render(<LoginScreen />);
    expect(screen.queryByText('💬')).toBeNull();
    expect(screen.getByLabelText('카카오톡')).toBeTruthy();
  });

  it('빠른 시작 badge 유지', () => {
    render(<LoginScreen />);
    expect(screen.getByText('3초 만에 시작')).toBeTruthy();
  });
});
