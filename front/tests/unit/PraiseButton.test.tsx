import React from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react-native';
import PraiseButton from '@/components/group/PraiseButton';
import { usePraiseActivityMutation } from '@/store/slices/activityApi';

jest.mock('@/store/slices/activityApi', () => ({
  usePraiseActivityMutation: jest.fn(),
}));

const mockMutation = usePraiseActivityMutation as unknown as jest.Mock;

describe('PraiseButton', () => {
  let praiseFn: jest.Mock;

  beforeEach(() => {
    praiseFn = jest.fn(() => ({ unwrap: () => Promise.resolve({ alreadyPraised: false }) }));
    mockMutation.mockReturnValue([praiseFn, { isLoading: false }]);
  });

  it('초기 상태 — "칭찬하기" 버튼 렌더', () => {
    render(<PraiseButton activityFeedId={7} groupId={20} />);
    expect(screen.getByLabelText('칭찬하기')).toBeTruthy();
  });

  it('탭하면 activityFeedId+groupId 로 praiseActivity 호출', async () => {
    render(<PraiseButton activityFeedId={7} groupId={20} />);
    await act(async () => {
      fireEvent.press(screen.getByLabelText('칭찬하기'));
    });
    expect(praiseFn).toHaveBeenCalledWith({ activityFeedId: 7, groupId: 20 });
  });

  it('성공 후 "칭찬함" 으로 바뀌고 재탭해도 다시 호출 안 함', async () => {
    render(<PraiseButton activityFeedId={7} groupId={20} />);
    await act(async () => {
      fireEvent.press(screen.getByLabelText('칭찬하기'));
    });
    expect(screen.getByLabelText('칭찬함')).toBeTruthy();
    expect(screen.queryByLabelText('칭찬하기')).toBeNull();
    expect(praiseFn).toHaveBeenCalledTimes(1);
  });

  it('실패해도 버튼이 그대로 남아 재시도 가능', async () => {
    praiseFn.mockReturnValue({ unwrap: () => Promise.reject(new Error('network')) });
    render(<PraiseButton activityFeedId={7} groupId={20} />);
    await act(async () => {
      fireEvent.press(screen.getByLabelText('칭찬하기'));
    });
    expect(screen.getByLabelText('칭찬하기')).toBeTruthy();
  });

  it('praisedByMe=true 로 시작하면 서버 진실源대로 바로 "칭찬함" 렌더 (다른 화면에서도 일관)', () => {
    render(<PraiseButton activityFeedId={7} groupId={20} praisedByMe />);
    expect(screen.getByLabelText('칭찬함')).toBeTruthy();
    expect(screen.queryByLabelText('칭찬하기')).toBeNull();
  });
});
