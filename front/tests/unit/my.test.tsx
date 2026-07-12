import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import MyScreen from '@/app/(tabs)/my';
import { getDisplayName, saveDisplayName } from '@/lib/auth/storage';
import { useUpdateUserNameMutation } from '@/store/slices/userApi';

jest.mock('expo-router', () => ({
  router: { back: jest.fn(), replace: jest.fn() },
}));

jest.mock('expo-constants', () => ({
  __esModule: true,
  default: { expoConfig: { version: '1.2.3' } },
}));

jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: React.ReactNode }) => children,
}));

jest.mock('@/lib/auth/storage', () => ({
  clearAuth: jest.fn().mockResolvedValue(undefined),
  getDisplayName: jest.fn().mockResolvedValue(null),
  saveDisplayName: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('@/store/slices/userApi', () => ({
  useUpdateUserNameMutation: jest.fn(),
}));

const mockUpdateUserName = useUpdateUserNameMutation as jest.Mock;

describe('MyScreen 닉네임 변경', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (getDisplayName as jest.Mock).mockResolvedValue(null);
    mockUpdateUserName.mockReturnValue([
      jest.fn().mockReturnValue({
        unwrap: jest.fn().mockResolvedValue({ name: '', email: null, profileUrl: null }),
      }),
      { isLoading: false },
    ]);
  });

  it('저장된 닉네임 없으면 기본 문구 "내 계정" 노출', async () => {
    render(<MyScreen />);
    await waitFor(() => expect(screen.getByText('내 계정')).toBeTruthy());
  });

  it('저장된 닉네임 있으면 마운트 시 로드해 표시', async () => {
    (getDisplayName as jest.Mock).mockResolvedValue('홍길동');
    render(<MyScreen />);
    await waitFor(() => expect(screen.getByText('홍길동')).toBeTruthy());
  });

  it('연필 아이콘 → 편집 모달에 현재 이름 프리필', async () => {
    (getDisplayName as jest.Mock).mockResolvedValue('홍길동');
    render(<MyScreen />);
    await waitFor(() => expect(screen.getByText('홍길동')).toBeTruthy());

    fireEvent.press(screen.getByLabelText('닉네임 변경'));
    expect(screen.getByDisplayValue('홍길동')).toBeTruthy();
  });

  it('빈 값 확인 → mutation 미호출(확인 버튼 disabled)', async () => {
    const mutateFn = jest.fn().mockReturnValue({
      unwrap: jest.fn().mockResolvedValue({ name: '', email: null, profileUrl: null }),
    });
    mockUpdateUserName.mockReturnValue([mutateFn, { isLoading: false }]);

    render(<MyScreen />);
    fireEvent.press(screen.getByLabelText('닉네임 변경'));
    fireEvent.changeText(screen.getByPlaceholderText('닉네임을 입력해주세요'), '');
    fireEvent.press(screen.getByLabelText('확인'));

    expect(mutateFn).not.toHaveBeenCalled();
  });

  it('유효한 이름 확인 → PATCH mutation 호출 + 서버 응답 name 으로 로컬 저장/즉시 반영', async () => {
    const unwrap = jest.fn().mockResolvedValue({ name: '새닉네임', email: null, profileUrl: null });
    const mutateFn = jest.fn().mockReturnValue({ unwrap });
    mockUpdateUserName.mockReturnValue([mutateFn, { isLoading: false }]);

    render(<MyScreen />);
    fireEvent.press(screen.getByLabelText('닉네임 변경'));
    fireEvent.changeText(screen.getByPlaceholderText('닉네임을 입력해주세요'), '새닉네임');
    await act(async () => {
      fireEvent.press(screen.getByLabelText('확인'));
    });

    expect(mutateFn).toHaveBeenCalledWith({ name: '새닉네임' });
    expect(saveDisplayName).toHaveBeenCalledWith('새닉네임');
    await waitFor(() => expect(screen.getByText('새닉네임')).toBeTruthy());
    expect(screen.queryByPlaceholderText('닉네임을 입력해주세요')).toBeNull(); // 모달 닫힘
  });

  it('mutation 실패 → 에러 얼럿, 모달 유지', async () => {
    const unwrap = jest.fn().mockRejectedValue(new Error('network'));
    const mutateFn = jest.fn().mockReturnValue({ unwrap });
    mockUpdateUserName.mockReturnValue([mutateFn, { isLoading: false }]);
    const alertSpy = jest.spyOn(require('react-native').Alert, 'alert').mockImplementation(() => {});

    render(<MyScreen />);
    fireEvent.press(screen.getByLabelText('닉네임 변경'));
    fireEvent.changeText(screen.getByPlaceholderText('닉네임을 입력해주세요'), '새닉네임');
    await act(async () => {
      fireEvent.press(screen.getByLabelText('확인'));
    });

    expect(alertSpy).toHaveBeenCalledWith('오류', '닉네임 변경에 실패했어요. 다시 시도해주세요.');
    expect(screen.getByPlaceholderText('닉네임을 입력해주세요')).toBeTruthy(); // 모달 유지

    alertSpy.mockRestore();
  });
});
