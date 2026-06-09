import { useState, useEffect, useCallback, useRef } from 'react';

export type HintStatus = 'ok' | 'warn' | 'loading';

export interface CameraHints {
  stability: HintStatus;
  brightness: HintStatus;
  tilt: HintStatus;
}

export interface CameraGuideResult {
  hints: CameraHints;
  allOk: boolean;
  reset: () => void;
  warnShake: () => void;
}

const STABILITY_DELAY_MS = 2500;
const SHAKE_RECOVER_MS = 2000;

export function useCameraGuide(): CameraGuideResult {
  const [stability, setStability] = useState<HintStatus>('loading');
  const stableTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const recoverTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const startStabilityTimer = useCallback(() => {
    if (stableTimerRef.current) clearTimeout(stableTimerRef.current);
    stableTimerRef.current = setTimeout(() => {
      setStability('ok');
    }, STABILITY_DELAY_MS);
  }, []);

  useEffect(() => {
    startStabilityTimer();
    return () => {
      if (stableTimerRef.current) clearTimeout(stableTimerRef.current);
      if (recoverTimerRef.current) clearTimeout(recoverTimerRef.current);
    };
  }, [startStabilityTimer]);

  const reset = useCallback(() => {
    if (recoverTimerRef.current) clearTimeout(recoverTimerRef.current);
    setStability('loading');
    startStabilityTimer();
  }, [startStabilityTimer]);

  const warnShake = useCallback(() => {
    if (stableTimerRef.current) clearTimeout(stableTimerRef.current);
    if (recoverTimerRef.current) clearTimeout(recoverTimerRef.current);
    setStability('warn');
    recoverTimerRef.current = setTimeout(() => {
      setStability('ok');
    }, SHAKE_RECOVER_MS);
  }, []);

  const allOk = stability === 'ok';

  return {
    hints: { stability, brightness: 'ok', tilt: 'ok' },
    allOk,
    reset,
    warnShake,
  };
}
