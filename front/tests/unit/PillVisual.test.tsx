import React from 'react';
import { render } from '@testing-library/react-native';
import PillVisual from '@/components/common/PillVisual';

describe('PillVisual', () => {
  it('기본 크기(32)로 렌더', () => {
    const { toJSON } = render(<PillVisual colorA="#A8D4FF" />);
    expect(toJSON()).toBeTruthy();
  });

  it('colorB 없으면 colorA 단색으로 렌더', () => {
    const { toJSON } = render(<PillVisual colorA="#0066FF" />);
    expect(toJSON()).toBeTruthy();
  });

  it('colorA, colorB 다른 색상으로 렌더', () => {
    const { toJSON } = render(<PillVisual colorA="#FFB3C1" colorB="#F5F5F5" />);
    expect(toJSON()).toBeTruthy();
  });

  it('dimmed=true 시 opacity 적용', () => {
    const { toJSON } = render(<PillVisual colorA="#C4B5FD" dimmed />);
    const root = toJSON() as any;
    expect(root.props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ opacity: 0.4 })]),
    );
  });

  it('size prop이 레이아웃에 반영', () => {
    const { toJSON } = render(<PillVisual colorA="#0066FF" size={48} />);
    const root = toJSON() as any;
    expect(root.props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ width: 48, height: 48 })]),
    );
  });
});
