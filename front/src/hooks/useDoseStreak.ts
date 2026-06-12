import { useMemo } from 'react';
import { useGetMonthAdherenceQuery } from '@/store/slices/scheduleApi';
import { deriveStreak, toMonthString, prevMonth } from '@/utils/calendarUtils';

export function useDoseStreak(today: string, todayComplete: boolean): number {
  const [year, month, day] = today.split('-').map(Number);
  const { data: currentMonth } = useGetMonthAdherenceQuery(toMonthString(year, month));

  const tentative = deriveStreak(currentMonth ?? {}, today, todayComplete);
  const reachesMonthStart = tentative - (todayComplete ? 1 : 0) >= day - 1;

  const previous = prevMonth(year, month);
  const { data: previousMonth } = useGetMonthAdherenceQuery(
    toMonthString(previous.year, previous.month),
    { skip: !currentMonth || !reachesMonthStart },
  );

  return useMemo(
    () => deriveStreak({ ...(previousMonth ?? {}), ...(currentMonth ?? {}) }, today, todayComplete),
    [previousMonth, currentMonth, today, todayComplete],
  );
}
