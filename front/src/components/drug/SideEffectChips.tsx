import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors, space, radius, typography } from '@/styles/tokens';

interface SideEffectChipsProps {
  sideEffects: { name: string; rate: number }[];
}

export default function SideEffectChips({ sideEffects }: SideEffectChipsProps) {
  if (sideEffects.length === 0) return null;
  return (
    <View style={styles.section}>
      <Text style={styles.title}>대표 부작용</Text>
      <View style={styles.chips}>
        {sideEffects.map(({ name, rate }) => (
          <View key={name} style={styles.chip}>
            <Text style={styles.chipName}>{name}</Text>
            <Text style={styles.chipRate}>{(rate * 100).toFixed(0)}%</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  section: { paddingHorizontal: space.s16, paddingTop: space.s12, paddingBottom: space.s16 },
  title: { ...typography.body1n, fontWeight: '700', marginBottom: space.s10, letterSpacing: -0.015 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space.s6 },
  chip: {
    flexDirection: 'row', alignItems: 'center', gap: space.s6,
    paddingHorizontal: space.s12, paddingVertical: space.s8,
    borderRadius: radius.full, backgroundColor: colors.fillNormal,
  },
  chipName: { ...typography.label2, color: colors.labelNormal },
  chipRate: { fontSize: 11, color: colors.labelAlternative, fontWeight: '600' },
});
