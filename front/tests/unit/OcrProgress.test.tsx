import React from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react-native';
import OcrProgress from '@/components/prescription/OcrProgress';
import { deriveOcrProgress, PROGRESS_CAP } from '@/hooks/useOcrProgress';

describe('deriveOcrProgress (순수 로직)', () => {
  it('0초: 업로드 active, 나머지 pending', () => {
    const s = deriveOcrProgress(0);
    expect(s.stages[0].status).toBe('active');
    expect(s.stages[1].status).toBe('pending');
    expect(s.stages[2].status).toBe('pending');
    expect(s.isSlow).toBe(false);
    expect(s.canRetry).toBe(false);
  });

  it('5초: 업로드 done, 인식 active', () => {
    const s = deriveOcrProgress(5000);
    expect(s.stages[0].status).toBe('done');
    expect(s.stages[1].status).toBe('active');
    expect(s.stages[2].status).toBe('pending');
  });

  it('35초: 인식 done, 매칭 active, isSlow=true', () => {
    const s = deriveOcrProgress(35000);
    expect(s.stages[1].status).toBe('done');
    expect(s.stages[2].status).toBe('active');
    expect(s.isSlow).toBe(true);
    expect(s.canRetry).toBe(false);
  });

  it('61초: canRetry=true, progress 0.95 cap', () => {
    const s = deriveOcrProgress(61000);
    expect(s.canRetry).toBe(true);
    expect(s.progress).toBe(PROGRESS_CAP);
  });

  it('progress: 30초 ≈ 0.5, 과대값은 0.95 cap', () => {
    expect(deriveOcrProgress(30000).progress).toBeCloseTo(0.5);
    expect(deriveOcrProgress(120000).progress).toBe(PROGRESS_CAP);
  });

  it('elapsedSec 내림', () => {
    expect(deriveOcrProgress(5900).elapsedSec).toBe(5);
  });
});

describe('OcrProgress 컴포넌트', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => { jest.clearAllTimers(); jest.useRealTimers(); });

  it('초기엔 기본 메시지 + 다시시도 버튼 없음', () => {
    render(<OcrProgress onRetry={jest.fn()} />);
    expect(screen.getByText('AI가 약을 분석하고 있어요')).toBeTruthy();
    expect(screen.queryByLabelText('다시 시도')).toBeNull();
  });

  it('30초 초과 시 slow 메시지', () => {
    render(<OcrProgress onRetry={jest.fn()} />);
    act(() => { jest.advanceTimersByTime(31000); });
    expect(screen.getByText('오래 걸리네요, 잠시만 더…')).toBeTruthy();
  });

  it('60초 초과 시 다시 시도 버튼 노출 + onRetry 호출', () => {
    const onRetry = jest.fn();
    render(<OcrProgress onRetry={onRetry} />);
    act(() => { jest.advanceTimersByTime(61000); });
    const btn = screen.getByLabelText('다시 시도');
    expect(btn).toBeTruthy();
    fireEvent.press(btn);
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});

describe('OcrProgress phase=failed', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => { jest.clearAllTimers(); jest.useRealTimers(); });

  it('실패 헤더 + ✗ 인식 실패 단계 + 두 버튼(다시시도/뒤로)', () => {
    const onRetry = jest.fn();
    const onBack = jest.fn();
    render(<OcrProgress phase="failed" onRetry={onRetry} onBack={onBack} />);

    expect(screen.getByText('약 인식에 실패했어요')).toBeTruthy();
    expect(screen.getByText('잠시 후 다시 시도해 주세요')).toBeTruthy();
    expect(screen.getByText('✗')).toBeTruthy();
    expect(screen.getByText('AI 약 인식 실패')).toBeTruthy();

    fireEvent.press(screen.getByLabelText('다시 시도'));
    expect(onRetry).toHaveBeenCalledTimes(1);
    fireEvent.press(screen.getByLabelText('뒤로'));
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('failed 에서 onBack 없으면 뒤로 버튼 미노출', () => {
    render(<OcrProgress phase="failed" onRetry={jest.fn()} />);
    expect(screen.getByLabelText('다시 시도')).toBeTruthy();
    expect(screen.queryByLabelText('뒤로')).toBeNull();
  });

  it('progressing 기본값: 실패 헤더 없음(회귀)', () => {
    render(<OcrProgress onRetry={jest.fn()} />);
    expect(screen.queryByText('약 인식에 실패했어요')).toBeNull();
    expect(screen.getByText('AI가 약을 분석하고 있어요')).toBeTruthy();
  });
});
