import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import DoseStepper from '../../src/components/prescription/DoseStepper';

describe('DoseStepper', () => {
  it('renders value and unit', () => {
    const { getByText } = render(
      <DoseStepper value={2} unit="정" onChange={jest.fn()} />
    );
    expect(getByText('2')).toBeTruthy();
    expect(getByText('정')).toBeTruthy();
  });

  it('calls onChange with incremented value', () => {
    const onChange = jest.fn();
    const { getByLabelText } = render(
      <DoseStepper value={1} unit="정" onChange={onChange} />
    );
    fireEvent.press(getByLabelText('복용량 늘리기'));
    expect(onChange).toHaveBeenCalledWith(2);
  });

  it('calls onChange with decremented value', () => {
    const onChange = jest.fn();
    const { getByLabelText } = render(
      <DoseStepper value={3} unit="정" onChange={onChange} />
    );
    fireEvent.press(getByLabelText('복용량 줄이기'));
    expect(onChange).toHaveBeenCalledWith(2);
  });

  it('does not go below min', () => {
    const onChange = jest.fn();
    const { getByLabelText } = render(
      <DoseStepper value={1} unit="정" min={1} onChange={onChange} />
    );
    fireEvent.press(getByLabelText('복용량 줄이기'));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('does not exceed max', () => {
    const onChange = jest.fn();
    const { getByLabelText } = render(
      <DoseStepper value={10} unit="정" max={10} onChange={onChange} />
    );
    fireEvent.press(getByLabelText('복용량 늘리기'));
    expect(onChange).not.toHaveBeenCalled();
  });
});
