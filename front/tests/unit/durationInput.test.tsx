import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import DurationField, { clampDuration } from '../../src/components/prescription/DurationField';

describe('clampDuration', () => {
  it('0/음수는 최소 1일로', () => {
    expect(clampDuration(0)).toBe(1);
    expect(clampDuration(-5)).toBe(1);
  });

  it('365 초과는 365로 cap', () => {
    expect(clampDuration(400)).toBe(365);
    expect(clampDuration(365)).toBe(365);
  });

  it('정상 범위는 그대로', () => {
    expect(clampDuration(1)).toBe(1);
    expect(clampDuration(5)).toBe(5);
    expect(clampDuration(30)).toBe(30);
  });

  it('NaN은 최소 1일로', () => {
    expect(clampDuration(NaN)).toBe(1);
  });

  it('소수는 내림', () => {
    expect(clampDuration(7.9)).toBe(7);
  });
});

describe('DurationField', () => {
  it('일수 입력 시 clamp 후 onChange', () => {
    const onChange = jest.fn();
    render(<DurationField valueDays={7} onChange={onChange} />);
    fireEvent.changeText(screen.getByLabelText('복약 일수 입력'), '400');
    expect(onChange).toHaveBeenCalledWith(365);
  });

  it('숫자 외 입력은 무시(빈값 시 onChange 호출 안 함)', () => {
    const onChange = jest.fn();
    render(<DurationField valueDays={7} onChange={onChange} />);
    fireEvent.changeText(screen.getByLabelText('복약 일수 입력'), 'abc');
    expect(onChange).not.toHaveBeenCalled();
  });

  it('무기한 토글 시 null 전송', () => {
    const onChange = jest.fn();
    render(<DurationField valueDays={7} onChange={onChange} />);
    fireEvent.press(screen.getByLabelText('무기한'));
    expect(onChange).toHaveBeenCalledWith(null);
  });

  it('무기한 상태에서 토글 해제 시 최소 1일', () => {
    const onChange = jest.fn();
    render(<DurationField valueDays={null} onChange={onChange} />);
    fireEvent.press(screen.getByLabelText('무기한'));
    expect(onChange).toHaveBeenCalledWith(1);
  });

  it('빠른 선택 칩 14일 전송', () => {
    const onChange = jest.fn();
    render(<DurationField valueDays={7} onChange={onChange} />);
    fireEvent.press(screen.getByLabelText('14일'));
    expect(onChange).toHaveBeenCalledWith(14);
  });
});
