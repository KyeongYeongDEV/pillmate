import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors, space } from '@/styles/tokens';

type AdherenceStatus = 'full' | 'partial' | 'miss' | 'today';

const ADHERENCE: Record<number, AdherenceStatus> = {
  1: 'full', 2: 'full', 3: 'full', 4: 'partial', 5: 'full', 6: 'full', 7: 'full',
  8: 'full', 9: 'partial', 10: 'full', 11: 'full', 12: 'full', 13: 'miss',
  14: 'partial', 15: 'full', 16: 'full', 17: 'full', 18: 'full', 19: 'partial',
  20: 'full', 21: 'full', 22: 'full', 23: 'miss', 24: 'today',
};

const DOT_COLOR: Record<AdherenceStatus, string> = {
  full: colors.statusPositive,
  partial: colors.statusCautionary,
  miss: colors.statusNegative,
  today: colors.primaryBase,
};

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const TODAY = 24;

// November 2025: Nov 1 = Saturday (column 6 in Sun=0 grid)
const CELLS: (number | null)[] = Array.from({ length: 35 }, (_, i) => {
  const d = i - 5;
  return d >= 1 && d <= 30 ? d : null;
});

const ROWS: (number | null)[][] = [];
for (let i = 0; i < CELLS.length; i += 7) {
  ROWS.push(CELLS.slice(i, i + 7));
}

interface DayCellProps { d: number | null; col: number }

function DayCell({ d, col }: DayCellProps) {
  if (d == null) return <View style={styles.cell} />;
  const status = ADHERENCE[d];
  const isToday = d === TODAY;
  return (
    <View style={[styles.cell, styles.dayCell]}>
      <View style={[styles.numCircle, isToday && styles.todayBg]}>
        <Text style={[
          styles.numText,
          isToday && styles.todayNumText,
          !isToday && col === 0 && styles.sunText,
          !isToday && col === 6 && styles.satText,
        ]}>{d}</Text>
      </View>
      {status && !isToday
        ? <View style={[styles.dot, { backgroundColor: DOT_COLOR[status] }]} />
        : <View style={styles.dotPlaceholder} />}
    </View>
  );
}

const MemoCell = React.memo(DayCell);

export default function CalendarGrid() {
  return (
    <View style={styles.container}>
      <View style={styles.row}>
        {WEEKDAYS.map((d, i) => (
          <View key={d} style={styles.cell}>
            <Text style={[styles.wdLabel, i === 0 && styles.sunText, i === 6 && styles.satText]}>{d}</Text>
          </View>
        ))}
      </View>
      {ROWS.map((row, ri) => (
        <View key={ri} style={styles.row}>
          {row.map((d, ci) => <MemoCell key={ci} d={d} col={ci} />)}
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
  todayNumText: { color: '#fff', fontWeight: '700' },
  sunText: { color: colors.statusNegative },
  satText: { color: colors.primaryBase },
  wdLabel: { fontSize: 11, fontWeight: '600', paddingVertical: 6, color: colors.labelAlternative },
  dot: { width: 6, height: 6, borderRadius: 3, marginTop: 2 },
  dotPlaceholder: { width: 6, height: 6, marginTop: 2 },
});
