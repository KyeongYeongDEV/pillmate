import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { colors, space, radius, typography } from '@/styles/tokens';

interface SourceCardProps {
  source: string | null;
}

const DEFAULT_SOURCE = '식품의약품안전처 의약품안전나라';

export default function SourceCard({ source }: SourceCardProps) {
  return (
    <View style={styles.container}>
      <View style={styles.badge}>
        <Text style={styles.badgeTxt}>식약</Text>
      </View>
      <View style={styles.body}>
        <Text style={styles.sourceLine}>출처: {source ?? DEFAULT_SOURCE}</Text>
      </View>
      <Feather name="external-link" size={16} color={colors.labelAlternative} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row', alignItems: 'center', gap: space.s10,
    paddingHorizontal: space.s14, paddingVertical: space.s12,
    backgroundColor: colors.bgAlt, borderRadius: radius.r12,
    marginHorizontal: space.s16, marginBottom: space.s24,
  },
  badge: {
    width: 28, height: 28, borderRadius: space.s6,
    backgroundColor: colors.bgNormal, borderWidth: 1, borderColor: colors.line,
    alignItems: 'center', justifyContent: 'center',
  },
  badgeTxt: { fontSize: 11, fontWeight: '700', color: colors.primaryNormal },
  body: { flex: 1 },
  sourceLine: { ...typography.caption1, color: colors.labelAlternative, lineHeight: 17 },
});
