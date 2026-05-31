import React from 'react';
import { render, screen } from '@testing-library/react-native';
import AvatarStack from '@/components/common/AvatarStack';

describe('AvatarStack', () => {
  it('이름 1개 — 이니셜 첫 글자 렌더', () => {
    render(<AvatarStack names={['박순자']} />);
    expect(screen.getByText('박')).toBeTruthy();
  });

  it('이름 3개 — 전부 렌더 (MAX_VISIBLE)', () => {
    render(<AvatarStack names={['박', '김', '이']} />);
    expect(screen.getByText('박')).toBeTruthy();
    expect(screen.getByText('김')).toBeTruthy();
    expect(screen.getByText('이')).toBeTruthy();
  });

  it('이름 4개 — 더보기 +1 표시', () => {
    render(<AvatarStack names={['박', '김', '이', '최']} />);
    expect(screen.getByText('+1')).toBeTruthy();
  });

  it('이름 5개 — 더보기 +2 표시', () => {
    render(<AvatarStack names={['박', '김', '이', '최', '강']} />);
    expect(screen.getByText('+2')).toBeTruthy();
  });

  it('빈 배열 — 아무것도 렌더 안함', () => {
    const { toJSON } = render(<AvatarStack names={[]} />);
    expect(toJSON()).toBeTruthy();
  });
});
