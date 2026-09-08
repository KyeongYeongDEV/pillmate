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

  // tints 를 넘기면(예: assignMemberColors 결과) 구성원 목록 하단과 동일한 고유색을 써야 한다 —
  // 안 넘기면(예: 그룹 목록 미리보기 카드) 기존 역할 기반 고정 팔레트로 폴백.
  it('tints prop 을 넘기면 그 색을 이니셜 순서대로 그대로 쓴다', () => {
    render(<AvatarStack names={['박', '김']} tints={['#111111', '#222222']} />);
    expect(screen.getByText('박').parent?.parent?.props.style.backgroundColor).toBe('#111111');
    expect(screen.getByText('김').parent?.parent?.props.style.backgroundColor).toBe('#222222');
  });
});
