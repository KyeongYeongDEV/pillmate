import React, { useMemo } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { colors, space } from '@/styles/tokens';
import { buildCalendarRows, type AdherenceLevel } from '@/utils/calendarUtils';

const DOT_COLOR: Record<AdherenceLevel, string> = {
  full: colors.statusPositive,
  partial: colors.statusCautionary,
  miss: colors.statusNegative,
};

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

export interface CalendarGridProps {
  year: number;
  month: number;
  selectedDate: string;
  today: string;
  onSelectDate: (date: string) => void;
  adherenceByDate?: Record<string, AdherenceLevel>;
}

interface DayCellProps {
  dateStr: string | null;
  col: number;
  isSelected: boolean;
  isToday: boolean;
  adherence?: AdherenceLevel;
  onPress: (date: string) => void;
}

function DayCell({ dateStr, col, isSelected, isToday, adherence, onPress }: DayCellProps) {
  if (dateStr === null) return <View style={styles.cell} />;

  const day = parseInt(dateStr.slice(8), 10);
  const isSun = col === 0;
  const isSat = col === 6;

  const circleBg =
    isToday ? styles.todayBg :
    isSelected ? styles.selectedBg : undefined;

  const numColor =
    isToday || isSelected ? styles.invertNumText :
    isSun ? styles.sunText :
    isSat ? styles.satText : undefined;

  const dayMonthStr = `${parseInt(dateStr.slice(5, 7), 10)}월 ${day}일`;

  return (
    <Pressable
      style={[styles.cell, styles.dayCell]}
      onPress={() => onPress(dateStr)}
      accessibilityLabel={dayMonthStr}
      accessibilityState={{ selected: isSelected }}
      accessibilityRole="button"
    >
      <View style={[styles.numCircle, circleBg]}>
        <Text style={[styles.numText, numColor]}>{day}</Text>
      </View>
      {adherence
        ? <View style={[styles.dot, { backgroundColor: DOT_COLOR[adherence] }]} />
        : <View style={styles.dotPlaceholder} />}
    </Pressable>
  );
}

const MemoCell = React.memo(DayCell);

export default function CalendarGrid({
  year, month, selectedDate, today, onSelectDate, adherenceByDate,
}: CalendarGridProps) {
  const rows = useMemo(() => buildCalendarRows(year, month), [year, month]);

  return (
    <View style={styles.container}>
      <View style={styles.row}>
        {WEEKDAYS.map((d, i) => (
          <View key={d} style={styles.cell}>
            <Text style={[styles.wdLabel, i === 0 && styles.sunText, i === 6 && styles.satText]}>
              {d}
            </Text>
          </View>
        ))}
      </View>
      {rows.map((row, ri) => (
        <View key={ri} style={styles.row}>
          {row.map((dateStr, ci) => (
            <MemoCell
              key={ci}
              dateStr={dateStr}
              col={ci}
              isSelected={dateStr === selectedDate}
              isToday={dateStr === today}
              adherence={dateStr ? adherenceByDate?.[dateStr] : undefined}
              onPress={onSelectDate}
            />
          ))}
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingHorizontal: space.s16 },
  row: { flexDirection: 'row' },
  cell: { flex: 1, alignItems: 'center' },
  dayCell: { paddingVertical: 3 },
  numCircle: { width: 32, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  numText: { fontSize: 14, fontWeight: '500', color: colors.labelNormal },
  todayBg: { backgroundColor: colors.labelNormal },
  selectedBg: { backgroundColor: colors.primaryBase },
  invertNumText: { color: '#fff', fontWeight: '700' },
  sunText: { color: colors.statusNegative },
  satText: { color: colors.primaryBase },
  wdLabel: { fontSize: 11, fontWeight: '600', paddingVertical: 6, color: colors.labelAlternative },
  dot: { width: 6, height: 6, borderRadius: 3, marginTop: 2 },
  dotPlaceholder: { width: 6, height: 6, marginTop: 2 },
});
