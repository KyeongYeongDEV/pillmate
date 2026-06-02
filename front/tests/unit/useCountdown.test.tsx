import React from 'react';
import { render, act } from '@testing-library/react-native';
import { Text } from 'react-native';
import { useCountdown } from '@/hooks/useCountdown';

function Probe({ expiresAt, onExpire }: { expiresAt: string | null; onExpire?: () => void }) {
  const { remainingSeconds, isExpired } = useCountdown(expiresAt, onExpire);
  return (
    <>
      <Text testID="sec">{remainingSeconds}</Text>
      <Text testID="exp">{isExpired ? 'yes' : 'no'}</Text>
    </>
  );
}

describe('useCountdown', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-06-02T00:00:00Z'));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('expiresAt = now + 60s → remainingSeconds 60, isExpired false', () => {
    const expiresAt = new Date(Date.now() + 60_000).toISOString();
    const { getByTestId } = render(<Probe expiresAt={expiresAt} />);
    expect(getByTestId('sec').props.children).toBe(60);
    expect(getByTestId('exp').props.children).toBe('no');
  });

  it('1초 advance → remainingSeconds 59', () => {
    const expiresAt = new Date(Date.now() + 60_000).toISOString();
    const { getByTestId } = render(<Probe expiresAt={expiresAt} />);
    act(() => { jest.advanceTimersByTime(1000); });
    expect(getByTestId('sec').props.children).toBe(59);
  });

  it('60초 advance → 0 + isExpired true + onExpire 1회 호출', () => {
    const onExpire = jest.fn();
    const expiresAt = new Date(Date.now() + 60_000).toISOString();
    const { getByTestId } = render(<Probe expiresAt={expiresAt} onExpire={onExpire} />);
    act(() => { jest.advanceTimersByTime(60_000); });
    expect(getByTestId('sec').props.children).toBe(0);
    expect(getByTestId('exp').props.children).toBe('yes');
    expect(onExpire).toHaveBeenCalledTimes(1);
  });

  it('expiresAt = null → 0 + isExpired true + onExpire 미호출', () => {
    const onExpire = jest.fn();
    const { getByTestId } = render(<Probe expiresAt={null} onExpire={onExpire} />);
    expect(getByTestId('sec').props.children).toBe(0);
    expect(getByTestId('exp').props.children).toBe('yes');
    expect(onExpire).not.toHaveBeenCalled();
  });

  it('이미 만료된 expiresAt (과거) → 0 + isExpired true', () => {
    const expiresAt = new Date(Date.now() - 10_000).toISOString();
    const { getByTestId } = render(<Probe expiresAt={expiresAt} />);
    expect(getByTestId('sec').props.children).toBe(0);
    expect(getByTestId('exp').props.children).toBe('yes');
  });

  it('onExpire 60초 도달 후 추가 advance → 추가 호출 X (1회만)', () => {
    const onExpire = jest.fn();
    const expiresAt = new Date(Date.now() + 60_000).toISOString();
    render(<Probe expiresAt={expiresAt} onExpire={onExpire} />);
    act(() => { jest.advanceTimersByTime(60_000); });
    act(() => { jest.advanceTimersByTime(5_000); });
    expect(onExpire).toHaveBeenCalledTimes(1);
  });
});
