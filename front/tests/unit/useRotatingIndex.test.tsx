import React from 'react';
import { render, act } from '@testing-library/react-native';
import { Text } from 'react-native';
import { useRotatingIndex } from '@/hooks/useRotatingIndex';

const INTERVAL = 10_000;

function Probe({ count }: { count: number }) {
  const index = useRotatingIndex(count, INTERVAL);
  return <Text testID="idx">{index}</Text>;
}

describe('useRotatingIndex', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it('count 3 → 10초마다 인덱스 증가', () => {
    const { getByTestId } = render(<Probe count={3} />);
    expect(getByTestId('idx').props.children).toBe(0);

    act(() => { jest.advanceTimersByTime(INTERVAL); });
    expect(getByTestId('idx').props.children).toBe(1);

    act(() => { jest.advanceTimersByTime(INTERVAL); });
    expect(getByTestId('idx').props.children).toBe(2);
  });

  it('마지막 인덱스에서 다음 tick → 0 으로 wrap-around', () => {
    const { getByTestId } = render(<Probe count={2} />);
    act(() => { jest.advanceTimersByTime(INTERVAL); });
    expect(getByTestId('idx').props.children).toBe(1);

    act(() => { jest.advanceTimersByTime(INTERVAL); });
    expect(getByTestId('idx').props.children).toBe(0);
  });

  it('count 1 → 타이머 없이 0 고정', () => {
    const { getByTestId } = render(<Probe count={1} />);
    act(() => { jest.advanceTimersByTime(INTERVAL * 5); });
    expect(getByTestId('idx').props.children).toBe(0);
  });

  it('count 0 → 0 유지', () => {
    const { getByTestId } = render(<Probe count={0} />);
    act(() => { jest.advanceTimersByTime(INTERVAL * 3); });
    expect(getByTestId('idx').props.children).toBe(0);
  });

  it('count 감소 시 인덱스 0 으로 리셋', () => {
    const { getByTestId, rerender } = render(<Probe count={3} />);
    act(() => { jest.advanceTimersByTime(INTERVAL * 2); });
    expect(getByTestId('idx').props.children).toBe(2);

    rerender(<Probe count={1} />);
    expect(getByTestId('idx').props.children).toBe(0);
  });
});
