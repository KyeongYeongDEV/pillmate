import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors, typography, space, scale } from '@/styles/tokens';

interface TabHeaderProps {
  title?: string;
  subtitle?: string;
  left?: React.ReactNode;
  right?: React.ReactNode;
}

export default function TabHeader({ title, subtitle, left, right }: TabHeaderProps) {
  return (
    <View style={styles.header}>
      <View style={styles.slotLeft}>{left ?? null}</View>
      <View style={styles.center}>
        {title ? <Text style={styles.title}>{title}</Text> : null}
        {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      </View>
      <View style={styles.slotRight}>{right ?? null}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.s16,
    paddingVertical: space.s12,
    backgroundColor: colors.bgNormal,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
  },
  slotLeft: { minWidth: scale(40), alignItems: 'flex-start', justifyContent: 'center' },
  slotRight: { minWidth: scale(40), alignItems: 'flex-end', justifyContent: 'center' },
  center: { flex: 1, alignItems: 'center', gap: 2 },
  title: { ...typography.headline1, color: colors.labelNormal },
  subtitle: { fontSize: scale(11), color: colors.labelAlternative, fontWeight: '500' },
});
