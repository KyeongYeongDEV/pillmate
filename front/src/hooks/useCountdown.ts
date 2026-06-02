import { useEffect, useRef, useState } from 'react';

interface UseCountdownResult {
  remainingSeconds: number;
  isExpired: boolean;
}

function computeRemaining(expiresAt: string | null): number {
  if (!expiresAt) return 0;
  const ms = new Date(expiresAt).getTime() - Date.now();
  if (Number.isNaN(ms)) return 0;
  return Math.max(0, Math.floor(ms / 1000));
}

export function useCountdown(
  expiresAt: string | null,
  onExpire?: () => void,
): UseCountdownResult {
  const [remaining, setRemaining] = useState<number>(() => computeRemaining(expiresAt));
  const firedRef = useRef(false);

  useEffect(() => {
    firedRef.current = false;
    setRemaining(computeRemaining(expiresAt));
    if (!expiresAt) return;

    const tick = () => {
      const next = computeRemaining(expiresAt);
      setRemaining(next);
      if (next === 0 && !firedRef.current) {
        firedRef.current = true;
        onExpire?.();
      }
    };

    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [expiresAt, onExpire]);

  return { remainingSeconds: remaining, isExpired: remaining === 0 };
}
