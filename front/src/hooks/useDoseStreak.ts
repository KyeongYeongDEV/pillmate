import { useMemo } from 'react';
import { useGetMonthAdherenceQuery } from '@/store/slices/scheduleApi';
import {
  deriveStreak, isStreakUnbrokenThrough, toDateString, toMonthString, prevMonth,
} from '@/utils/calendarUtils';

export function useDoseStreak(today: string, todayComplete: boolean): number {
  const [year, month] = today.split('-').map(Number);
  const { data: currentMonth } = useGetMonthAdherenceQuery(toMonthString(year, month));

  const monthStart = toDateString(year, month, 1);
  const reachesMonthStart = isStreakUnbrokenThrough(currentMonth ?? {}, today, monthStart);

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
