import { useEffect, useRef, useState } from 'react';
import { OCR_SLOW_THRESHOLD_MS, OCR_RETRY_THRESHOLD_MS } from './useOcrInFlight';

// 실제 BE 진행률 push가 없으므로 시간 베이스 가짜 progress (완료 전까지 95%에서 정지).
export const PROGRESS_MAX_SECONDS = 60;
export const PROGRESS_CAP = 0.95;
export const PROGRESS_TICK_MS = 500;
export const STAGE_UPLOAD_END_SEC = 3;
export const STAGE_RECOGNIZE_END_SEC = 30;

export type StageStatus = 'done' | 'active' | 'pending';

export interface OcrStage {
  key: string;
  label: string;
  status: StageStatus;
}

export interface OcrProgressState {
  elapsedSec: number;
  progress: number; // 0..PROGRESS_CAP
  stages: OcrStage[];
  isSlow: boolean; // 30초 초과
  canRetry: boolean; // 60초 초과
}

function statusFor(elapsedSec: number, startSec: number, endSec: number): StageStatus {
  if (elapsedSec >= endSec) return 'done';
  if (elapsedSec >= startSec) return 'active';
  return 'pending';
}

export function deriveOcrProgress(elapsedMs: number): OcrProgressState {
  const elapsedSec = Math.max(0, Math.floor(elapsedMs / 1000));
  const progress = Math.min(PROGRESS_CAP, Math.max(0, elapsedMs) / (PROGRESS_MAX_SECONDS * 1000));
  return {
    elapsedSec,
    progress,
    stages: [
      { key: 'upload', label: '이미지 업로드', status: statusFor(elapsedSec, 0, STAGE_UPLOAD_END_SEC) },
      { key: 'recognize', label: 'AI 약 인식', status: statusFor(elapsedSec, STAGE_UPLOAD_END_SEC, STAGE_RECOGNIZE_END_SEC) },
      { key: 'match', label: '약 정보 매칭', status: statusFor(elapsedSec, STAGE_RECOGNIZE_END_SEC, Number.POSITIVE_INFINITY) },
    ],
    isSlow: elapsedMs >= OCR_SLOW_THRESHOLD_MS,
    canRetry: elapsedMs >= OCR_RETRY_THRESHOLD_MS,
  };
}

export function useOcrProgress(active: boolean): OcrProgressState {
  const [elapsedMs, setElapsedMs] = useState(0);
  const startRef = useRef<number | null>(null);

  useEffect(() => {
    if (!active) {
      startRef.current = null;
      setElapsedMs(0);
      return;
    }
    startRef.current = Date.now();
    setElapsedMs(0);
    const timer = setInterval(() => {
      if (startRef.current != null) setElapsedMs(Date.now() - startRef.current);
    }, PROGRESS_TICK_MS);
    return () => clearInterval(timer);
  }, [active]);

  return deriveOcrProgress(elapsedMs);
}
