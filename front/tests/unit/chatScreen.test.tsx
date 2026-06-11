import React from 'react';
import { render } from '@testing-library/react-native';
import ChatScreen from '../../src/app/(tabs)/chat';

jest.mock('../../src/store/slices/chatApi', () => ({
  useSendMessageMutation: () => [jest.fn(), { isLoading: false }],
  chatApiSlice: {
    reducerPath: 'chatApi',
    reducer: (state = {}) => state,
    middleware: (api: unknown) => api,
  },
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn(), back: jest.fn() },
}));

jest.mock('@expo/vector-icons', () => {
  const { Text } = require('react-native');
  return {
    Feather: ({ name }: { name: string }) => <Text testID={`feather-${name}`}>{name}</Text>,
  };
});

describe('ChatScreen — CHAT_ENABLED=false (잠금)', () => {
  it('웰컴 메시지 렌더: "안녕하세요, PillMate AI예요"', () => {
    const { getByText } = render(<ChatScreen />);
    expect(getByText(/안녕하세요, PillMate AI예요/)).toBeTruthy();
  });

  it('웰컴 메시지에 "곧 오픈" 문구 포함', () => {
    const { getByText } = render(<ChatScreen />);
    expect(getByText(/약 상담 기능은 곧 오픈/)).toBeTruthy();
  });

  it('입력창 placeholder: "준비 중인 기능이에요"', () => {
    const { getByPlaceholderText } = render(<ChatScreen />);
    expect(getByPlaceholderText('준비 중인 기능이에요')).toBeTruthy();
  });

  it('TextInput editable=false (잠금 상태)', () => {
    const { getByPlaceholderText } = render(<ChatScreen />);
    const input = getByPlaceholderText('준비 중인 기능이에요');
    expect(input.props.editable).toBe(false);
  });

  it('전송 버튼 disabled=true', () => {
    const { getByLabelText } = render(<ChatScreen />);
    const sendBtn = getByLabelText('전송');
    expect(sendBtn.props.accessibilityState?.disabled).toBe(true);
  });

  it('🔒 아이콘 표시', () => {
    const { getByText } = render(<ChatScreen />);
    expect(getByText('🔒')).toBeTruthy();
  });

  it('헤더 "곧 오픈 예정" 표시', () => {
    const { getByText } = render(<ChatScreen />);
    expect(getByText('곧 오픈 예정')).toBeTruthy();
  });
});
