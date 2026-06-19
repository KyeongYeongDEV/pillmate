import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { scale, colors, radius, space } from '@/styles/tokens';
import type { SlotDotStatus } from '@/lib/scheduleUtils';

const STATUS_A11Y: Record<SlotDotStatus, string> = {
  done: '복용 완료',
  missed: '기록 없음',
  upcoming: '복용 예정',
};

export interface DoseDot {
  label: string;
  status: SlotDotStatus;
}

interface DoseStatusRowProps {
  dots: DoseDot[];
  doneCount: number;
  totalCount: number;
  streak: number;
  showStreak: boolean;
}

export default function DoseStatusRow({
  dots, doneCount, totalCount, streak, showStreak,
}: DoseStatusRowProps) {
  if (totalCount === 0) return null;
  return (
    <View style={styles.row}>
      <View style={styles.dots}>
        {dots.map((dot, index) => (
          <Dot key={`${dot.label}-${index}`} label={dot.label} status={dot.status} />
        ))}
      </View>
      <Text style={styles.count}>{doneCount}/{totalCount}</Text>
      {showStreak && <Text style={styles.streak}>🔥 {streak}일 연속 완료 중</Text>}
    </View>
  );
}

function Dot({ label, status }: DoseDot) {
  return (
    <View
      accessibilityLabel={`${label} ${STATUS_A11Y[status]}`}
      style={[styles.dotBase, dotStyles[status]]}
    >
      {status === 'done'
        ? <Feather name="check" size={scale(10)} color={colors.bgNormal} />
        : <Text style={styles.mark}>!</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: space.s8, marginTop: space.s6 },
  dots: { flexDirection: 'row', alignItems: 'center', gap: space.s4 },
  dotBase: {
    height: scale(13), minWidth: scale(13), borderRadius: radius.full,
    alignItems: 'center', justifyContent: 'center',
  },
  count: { fontSize: scale(13), fontWeight: '700', color: colors.labelNormal },
  streak: { fontSize: scale(13), fontWeight: '600', color: colors.labelAlternative },
  mark: { fontSize: scale(10), fontWeight: '700', color: colors.bgNormal, lineHeight: scale(12) },
});

const dotStyles = StyleSheet.create({
  done: { backgroundColor: colors.statusPositive },
  missed: { backgroundColor: colors.statusNegative },
  upcoming: { backgroundColor: colors.fillStrong },
});
