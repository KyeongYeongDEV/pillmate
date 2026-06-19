import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { scale, colors, space, radius, typography } from '@/styles/tokens';

interface DrugInfoSectionProps {
  title: string;
  text: string | null;
}

const EMPTY_TEXT = '정보 없음';

export default function DrugInfoSection({ title, text }: DrugInfoSectionProps) {
  const body = text?.trim() ? text.trim() : EMPTY_TEXT;
  const isEmpty = body === EMPTY_TEXT;
  return (
    <View style={styles.section}>
      <Text style={styles.title}>{title}</Text>
      <Text style={[styles.body, isEmpty && styles.empty]}>{body}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  section: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line,
    padding: space.s16, marginHorizontal: space.s16, marginBottom: space.s12,
  },
  title: { ...typography.headline2, color: colors.labelNormal, fontWeight: '700', marginBottom: space.s8 },
  body: { ...typography.body2r, color: colors.labelNormal, lineHeight: scale(22) },
  empty: { color: colors.labelAssistive },
});
