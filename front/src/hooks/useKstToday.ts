import { useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { getKstToday } from '@/utils/calendarUtils';

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

export function msUntilNextKstMidnight(now: Date): number {
  const kstNow = now.getTime() + KST_OFFSET_MS;
  const msSinceKstMidnight = ((kstNow % DAY_MS) + DAY_MS) % DAY_MS;
  return DAY_MS - msSinceKstMidnight;
}

export function useKstToday(): string {
  const [today, setToday] = useState(getKstToday());
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let cancelled = false;

    const scheduleNextMidnight = () => {
      timerRef.current = setTimeout(() => {
        if (cancelled) return;
        setToday(getKstToday());
        scheduleNextMidnight();
      }, msUntilNextKstMidnight(new Date()));
    };

    scheduleNextMidnight();

    const subscription = AppState.addEventListener('change', (state) => {
      if (state !== 'active') return;
      setToday((prev) => {
        const current = getKstToday();
        return current !== prev ? current : prev;
      });
    });

    return () => {
      cancelled = true;
      if (timerRef.current) clearTimeout(timerRef.current);
      subscription.remove();
    };
  }, []);

  return today;
}
