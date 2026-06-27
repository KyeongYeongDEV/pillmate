import { useEffect, useRef, useState } from 'react';

export const INSIGHT_POLL_INTERVAL_MS = 3000;
export const INSIGHT_POLL_MAX_ATTEMPTS = 3;

interface Args {
  hasData: boolean;
  hasInsights: boolean;
  refetch: () => void;
}

// 등록 직후 insight가 아직 없으면 3초 간격 최대 3회 refetch (BE listener 비동기 도착 대비).
// 도착(hasInsights) 또는 MAX 도달 시 중단. 반환값은 '생성 중…' placeholder 노출 여부.
export function useInsightPolling({ hasData, hasInsights, refetch }: Args): boolean {
  const [waiting, setWaiting] = useState(false);
  const attemptsRef = useRef(0);

  useEffect(() => {
    if (!hasData || hasInsights) {
      setWaiting(false);
      return;
    }

    attemptsRef.current = 0;
    setWaiting(true);

    const timer = setInterval(() => {
      if (attemptsRef.current >= INSIGHT_POLL_MAX_ATTEMPTS) {
        clearInterval(timer);
        setWaiting(false);
        return;
      }
      refetch();
      attemptsRef.current += 1;
    }, INSIGHT_POLL_INTERVAL_MS);

    return () => clearInterval(timer);
  }, [hasData, hasInsights, refetch]);

  return waiting;
}
