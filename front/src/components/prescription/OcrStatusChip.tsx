import React, { memo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { OcrStatus } from '@/types/prescription';
import { ocrStatusChip } from '@/lib/ocrStatusLabel';
import { space, radius } from '@/styles/tokens';

function OcrStatusChip({ status }: { status: OcrStatus }) {
  const { label, color } = ocrStatusChip(status);
  return (
    <View style={[styles.chip, { borderColor: color }]} accessibilityLabel={`상태 ${label}`}>
      <View style={[styles.dot, { backgroundColor: color }]} />
      <Text style={[styles.label, { color }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    flexDirection: 'row', alignItems: 'center', gap: space.s4,
    paddingHorizontal: space.s8, paddingVertical: space.s4,
    borderRadius: radius.full, borderWidth: 1,
  },
  dot: { width: 6, height: 6, borderRadius: radius.full },
  label: { fontSize: 12, fontWeight: '700' },
});

export default memo(OcrStatusChip);
