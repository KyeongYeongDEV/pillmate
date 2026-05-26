import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors, space, radius, typography } from '@/styles/tokens';

interface Stat {
  label: string;
  value: string;
}

interface QuickStatsProps {
  dailyDose?: string;
  timeOfDay?: string;
  remainingDays?: number;
}

export default function QuickStats({ dailyDose, timeOfDay, remainingDays }: QuickStatsProps) {
  const stats: Stat[] = [
    { label: '일일 복용', value: dailyDose ?? 'N/A' },
    { label: '복용 시각', value: timeOfDay ?? 'N/A' },
    { label: '남은 일수', value: remainingDays != null ? `${remainingDays}일` : 'N/A' },
  ];
  return (
    <View style={styles.row}>
      {stats.map(({ label, value }) => (
        <View key={label} style={styles.card}>
          <Text style={styles.label}>{label}</Text>
          <Text style={styles.value}>{value}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row', gap: space.s8,
    marginHorizontal: space.s16, marginBottom: space.s28,
  },
  card: {
    flex: 1, backgroundColor: colors.bgNormal,
    borderWidth: 1, borderColor: colors.line,
    borderRadius: radius.r12, paddingVertical: space.s12, paddingHorizontal: space.s10,
    alignItems: 'center',
  },
  label: { ...typography.caption1, color: colors.labelAlternative, fontWeight: '600', letterSpacing: 0.03 },
  value: { ...typography.body1n, fontWeight: '700', marginTop: space.s4, letterSpacing: -0.012 },
});
