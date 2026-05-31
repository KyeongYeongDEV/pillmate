import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import FilterChips, { GroupFilter } from '@/components/group/FilterChips';

describe('FilterChips', () => {
  it('4개 필터 모두 렌더', () => {
    render(<FilterChips selected="전체" onSelect={jest.fn()} />);
    expect(screen.getByText('전체')).toBeTruthy();
    expect(screen.getByText('내가 환자')).toBeTruthy();
    expect(screen.getByText('내가 보호자')).toBeTruthy();
    expect(screen.getByText('비공개')).toBeTruthy();
  });

  it('선택된 필터 onSelect 호출', () => {
    const onSelect = jest.fn();
    render(<FilterChips selected="전체" onSelect={onSelect} />);
    fireEvent.press(screen.getByText('내가 보호자'));
    expect(onSelect).toHaveBeenCalledWith('내가 보호자');
  });

  it('선택된 필터 변경 — onSelect 재호출', () => {
    const onSelect = jest.fn();
    render(<FilterChips selected="내가 환자" onSelect={onSelect} />);
    fireEvent.press(screen.getByText('전체'));
    expect(onSelect).toHaveBeenCalledWith('전체');
  });

  it('비공개 필터 클릭 — onSelect("비공개") 호출', () => {
    const onSelect = jest.fn();
    render(<FilterChips selected="전체" onSelect={onSelect} />);
    fireEvent.press(screen.getByText('비공개'));
    expect(onSelect).toHaveBeenCalledWith('비공개');
  });
});
