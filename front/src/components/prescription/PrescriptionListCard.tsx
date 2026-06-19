import React, { memo } from 'react';
import { Pressable, View, Text, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import type { PrescriptionSummary } from '@/types/prescription';
import { formatMonthDay } from '@/utils/calendarUtils';
import { scale, colors, space, radius, shadows } from '@/styles/tokens';
import OcrStatusChip from './OcrStatusChip';

interface Props {
  item: PrescriptionSummary;
  onPress: (id: number) => void;
}

function PrescriptionListCard({ item, onPress }: Props) {
  return (
    <Pressable
      style={styles.card}
      onPress={() => onPress(item.id)}
      accessibilityRole="button"
      accessibilityLabel={`${formatMonthDay(item.prescribedAt)} 처방전, 약 ${item.drugCount}종`}
    >
      <View style={styles.body}>
        <View style={styles.topRow}>
          <Text style={styles.date}>{formatMonthDay(item.prescribedAt)}</Text>
          <OcrStatusChip status={item.ocrStatus} />
        </View>
        <Text style={styles.names} numberOfLines={1}>{summarize(item)}</Text>
      </View>
      <Feather name="chevron-right" size={scale(20)} color={colors.labelAssistive} />
    </Pressable>
  );
}

function summarize(item: PrescriptionSummary): string {
  if (item.drugNames) return `${item.drugNames} · ${item.drugCount}종`;
  return `약 ${item.drugCount}종`;
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row', alignItems: 'center', gap: space.s8,
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line,
    padding: space.s16, ...shadows.small,
  },
  body: { flex: 1, gap: space.s8 },
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  date: { fontSize: scale(15), fontWeight: '700', color: colors.labelNormal },
  names: { fontSize: scale(13), color: colors.labelAlternative },
});

export default memo(PrescriptionListCard);
