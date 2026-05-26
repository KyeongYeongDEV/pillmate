import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import PillVisual from '@/components/common/PillVisual';
import { colors, space, radius, typography } from '@/styles/tokens';
import type { DrugInteraction } from '@/types/prescription';

interface InteractionWarningCardProps {
  interactions: DrugInteraction[];
}

export default function InteractionWarningCard({ interactions }: InteractionWarningCardProps) {
  if (interactions.length === 0) return null;

  const first = interactions[0];

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Feather name="alert-triangle" size={18} color={colors.statusNegative} />
        <Text style={styles.headerText}>
          병용금기 {interactions.length}건
        </Text>
      </View>

      <View style={styles.drugRow}>
        <PillVisual size={28} colorA="#ffb3b3" colorB="#ffd6d6" />
        <Text style={styles.drugName} numberOfLines={1}>
          {first.name} ({first.category})
        </Text>
        <Feather name="chevron-right" size={18} color={colors.labelAlternative} />
      </View>

      <Text style={styles.description}>{first.description}</Text>

      {interactions.length > 1 && (
        <Text style={styles.more}>+{interactions.length - 1}건 더 보기</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#fff5f5', borderRadius: radius.r14, padding: space.s16,
    borderWidth: 1, borderColor: '#ffd6d6',
    marginHorizontal: space.s16, marginBottom: space.s16,
  },
  header: { flexDirection: 'row', alignItems: 'center', gap: space.s8, marginBottom: space.s12 },
  headerText: { ...typography.label1n, fontWeight: '700', color: colors.statusNegative },
  drugRow: {
    flexDirection: 'row', alignItems: 'center', gap: space.s10,
    backgroundColor: colors.bgNormal, borderRadius: radius.r8,
    padding: space.s10, marginBottom: space.s10,
  },
  drugName: { flex: 1, ...typography.label2, fontWeight: '600', color: colors.labelNormal },
  description: {
    ...typography.caption1, color: colors.statusNegative,
    lineHeight: 18,
  },
  more: {
    ...typography.caption1, color: colors.statusNegative,
    fontWeight: '600', marginTop: space.s8,
  },
});
