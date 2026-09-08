import React from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react-native';
import { useLocalSearchParams } from 'expo-router';
import GroupSettingsScreen from '@/app/(tabs)/group/[id]/settings';
import {
  useGetGroupDetailQuery,
  useRenameGroupMutation,
  useLeaveGroupMutation,
} from '@/store/slices/caregroupApi';

jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: React.ReactNode }) => children,
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));

jest.mock('expo-router', () => ({
  useLocalSearchParams: jest.fn(),
  router: { push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => false },
}));

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

jest.mock('@/store/slices/caregroupApi', () => ({
  useGetGroupDetailQuery: jest.fn(),
  useRenameGroupMutation: jest.fn(),
  useLeaveGroupMutation: jest.fn(),
}));

const mockParams = useLocalSearchParams as unknown as jest.Mock;
const mockDetailQuery = useGetGroupDetailQuery as unknown as jest.Mock;
const mockRename = useRenameGroupMutation as unknown as jest.Mock;
const mockLeave = useLeaveGroupMutation as unknown as jest.Mock;

let renameMutate: jest.Mock;
let leaveMutate: jest.Mock;

function setup(name = '할머니 댁') {
  mockParams.mockReturnValue({ id: '3' });
  mockDetailQuery.mockReturnValue({
    data: { groupId: 3, name, memberCount: 2, members: [], inviteCode: null, recentActivities: [] },
    isLoading: false,
    isError: false,
  });
  renameMutate = jest.fn(() => ({ unwrap: () => Promise.resolve() }));
  leaveMutate = jest.fn(() => ({ unwrap: () => Promise.resolve() }));
  mockRename.mockReturnValue([renameMutate, {}]);
  mockLeave.mockReturnValue([leaveMutate, {}]);
}

describe('그룹 설정 화면 — 이름 수정 + 나가기', () => {
  beforeEach(() => jest.clearAllMocks());

  it('현재 그룹 이름을 입력창 초기값으로 채운다', () => {
    setup('할머니 댁');
    render(<GroupSettingsScreen />);
    expect(screen.getByLabelText('그룹 이름').props.value).toBe('할머니 댁');
  });

  it('이름을 바꾸지 않으면 저장 버튼이 비활성이다', () => {
    setup('할머니 댁');
    render(<GroupSettingsScreen />);
    expect(screen.getByLabelText('저장').props.accessibilityState.disabled).toBe(true);
  });

  it('이름을 바꾸면 renameGroup 뮤테이션을 올바른 인자로 호출한다', async () => {
    setup('할머니 댁');
    render(<GroupSettingsScreen />);
    fireEvent.changeText(screen.getByLabelText('그룹 이름'), '우리 가족');
    await act(async () => {
      fireEvent.press(screen.getByLabelText('저장'));
    });
    expect(renameMutate).toHaveBeenCalledWith({ groupId: 3, name: '우리 가족' });
  });

  it('공백만 입력하면 저장 버튼이 비활성이다', () => {
    setup('할머니 댁');
    render(<GroupSettingsScreen />);
    fireEvent.changeText(screen.getByLabelText('그룹 이름'), '   ');
    expect(screen.getByLabelText('저장').props.accessibilityState.disabled).toBe(true);
  });

  it('그룹 나가기 버튼을 렌더한다', () => {
    setup();
    render(<GroupSettingsScreen />);
    expect(screen.getByLabelText('그룹 나가기')).toBeTruthy();
  });
});
