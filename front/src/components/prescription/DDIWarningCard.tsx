import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { scale, colors, space, radius } from '@/styles/tokens';
import type { InteractionSeverity, InteractionWarning } from '@/types/prescription';

interface SeverityStyle {
  label: string;
  bg: string;
  fg: string;
  border: string;
  icon: string;
}

export const DDI_SEVERITY_STYLE: Record<InteractionSeverity, SeverityStyle> = {
  CRITICAL: { label: '위험', bg: colors.red95,    fg: colors.red40,            border: colors.red50,         icon: '⚠️' },
  HIGH:     { label: '주의', bg: colors.orange95, fg: colors.orange40,         border: colors.orange40,      icon: '⚠️' },
  MEDIUM:   { label: '보통', bg: colors.yellow95, fg: colors.yellow40,         border: colors.yellow40,      icon: 'ℹ️' },
  LOW:      { label: '경미', bg: colors.fillNormal, fg: colors.labelAlternative, border: colors.line,         icon: 'ℹ️' },
};

interface DDIWarningCardProps {
  warning: InteractionWarning;
}

function DDIWarningCard({ warning }: DDIWarningCardProps) {
  const style = DDI_SEVERITY_STYLE[warning.severity];
  return (
    <View
      style={[styles.card, { backgroundColor: style.bg, borderColor: style.border }]}
      accessibilityRole="alert"
      accessibilityLabel={`병용금기 ${warning.nameA} 와(과) ${warning.nameB} — ${warning.severity}`}
    >
      <View style={styles.header}>
        <Text style={styles.icon}>{style.icon}</Text>
        <View style={styles.headerText}>
          <Text style={[styles.pair, { color: style.fg }]} numberOfLines={2}>
            {warning.nameA} ↔ {warning.nameB}
          </Text>
        </View>
        <View style={[styles.chip, { backgroundColor: style.fg }]}>
          <Text style={styles.chipText}>{style.label}</Text>
        </View>
      </View>
      <Text style={styles.description} numberOfLines={4}>
        {warning.description}
      </Text>
      <View style={styles.sourceChip}>
        <Text style={styles.sourceText}>출처: {warning.source}</Text>
      </View>
    </View>
  );
}

export default React.memo(DDIWarningCard);

const styles = StyleSheet.create({
  card: {
    borderWidth: 1,
    borderRadius: radius.r12,
    padding: space.s14,
    gap: space.s8,
  },
  header: { flexDirection: 'row', alignItems: 'center', gap: space.s8 },
  icon: { fontSize: scale(18) },
  headerText: { flex: 1 },
  pair: { fontSize: scale(14), fontWeight: '700', letterSpacing: -0.01 },
  chip: {
    paddingHorizontal: space.s8, paddingVertical: 4,
    borderRadius: radius.full,
  },
  chipText: { fontSize: scale(11), fontWeight: '700', color: colors.staticWhite, letterSpacing: 0.04 },
  description: { fontSize: scale(13), color: colors.labelNormal, lineHeight: scale(18) },
  sourceChip: { alignSelf: 'flex-start' },
  sourceText: { fontSize: scale(11), color: colors.labelAlternative, fontWeight: '600' },
});
