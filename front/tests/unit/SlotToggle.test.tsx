import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import SlotToggle from '../../src/components/prescription/SlotToggle';
import type { DrugSlots } from '../../src/types/prescription';

const DEFAULT_SLOTS: DrugSlots = { morning: false, noon: false, evening: false, bedtime: false };

describe('SlotToggle', () => {
  it('renders 4 slots', () => {
    const { getByLabelText } = render(
      <SlotToggle slots={DEFAULT_SLOTS} onChange={jest.fn()} />
    );
    expect(getByLabelText('아침 복용')).toBeTruthy();
    expect(getByLabelText('점심 복용')).toBeTruthy();
    expect(getByLabelText('저녁 복용')).toBeTruthy();
    expect(getByLabelText('취침전 복용')).toBeTruthy();
  });

  it('calls onChange with toggled slot', () => {
    const onChange = jest.fn();
    const { getByLabelText } = render(
      <SlotToggle slots={DEFAULT_SLOTS} onChange={onChange} />
    );
    fireEvent.press(getByLabelText('아침 복용'));
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ morning: true })
    );
  });

  it('toggles off an active slot', () => {
    const onChange = jest.fn();
    const slots: DrugSlots = { morning: true, noon: false, evening: false, bedtime: false };
    const { getByLabelText } = render(
      <SlotToggle slots={slots} onChange={onChange} />
    );
    fireEvent.press(getByLabelText('아침 복용'));
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ morning: false })
    );
  });
});
