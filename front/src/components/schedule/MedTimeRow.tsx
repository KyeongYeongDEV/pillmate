import React, { useCallback } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import Icon from '@/components/common/Icon';
import { colors, space } from '@/styles/tokens';
import { MFDS_SOURCE } from '@/lib/constants';
import type { MedSlot } from '@/types/schedule';

interface Props {
  slot: MedSlot;
  isFirst?: boolean;
  onPress?: (slot: MedSlot) => void;
  readOnly?: boolean;
}

function MedTimeRow({ slot, isFirst, onPress, readOnly }: Props) {
  const done = slot.state === 'done';
  const now  = !readOnly && slot.state === 'now';
  const handlePress = useCallback(() => onPress?.(slot), [onPress, slot]);

  const rowContent = (
    <View style={[styles.row, !isFirst && styles.borderTop]}>
      <View style={styles.timeCol}>
        <Text style={[styles.time, done && styles.muted]}>{slot.time}</Text>
        <Text style={styles.timeLabel}>{slot.label}</Text>
      </View>
      <View style={styles.divider} />
      <View style={styles.itemsCol}>
        {slot.items.map((it, i) => (
          <Text key={i} style={[styles.item, done && styles.muted, done && styles.strike]}>{it}</Text>
        ))}
        <Text style={styles.source}>출처: {MFDS_SOURCE}</Text>
      </View>
      <View
        testID="dose-circle"
        style={[
          styles.circle,
          done && (readOnly ? styles.circleDoneReadOnly : styles.circleDone),
          now && styles.circleNow,
        ]}
      >
        {done && <Icon name="check" size={18} color="#fff" />}
      </View>
    </View>
  );

  if (onPress) {
    return (
      <Pressable
        onPress={handlePress}
        accessibilityRole="button"
        accessibilityLabel={`${slot.label} ${slot.time} 복용 체크`}
      >
        {rowContent}
      </Pressable>
    );
  }
  return rowContent;
}

export default React.memo(MedTimeRow);

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row', alignItems: 'center',
    gap: space.s14, paddingVertical: space.s14, paddingHorizontal: space.s16,
  },
  borderTop: { borderTopWidth: 1, borderTopColor: colors.line },
  timeCol: { width: 48, alignItems: 'center' },
  time: { fontSize: 15, fontWeight: '700', color: colors.labelNormal },
  timeLabel: { fontSize: 11, color: colors.labelAlternative, marginTop: 1 },
  muted: { color: colors.labelAlternative },
  strike: { textDecorationLine: 'line-through' },
  divider: { width: 1, height: 36, backgroundColor: colors.line },
  itemsCol: { flex: 1, gap: 1 },
  item: { fontSize: 14, fontWeight: '500', color: colors.labelNormal, lineHeight: 20 },
  source: { fontSize: 10, color: colors.labelAssistive, marginTop: 3 },
  circle: {
    width: 32, height: 32, borderRadius: 16,
    borderWidth: 1.5, borderColor: colors.line,
    alignItems: 'center', justifyContent: 'center',
  },
  circleDone: { backgroundColor: colors.statusPositive, borderWidth: 0 },
  circleDoneReadOnly: { backgroundColor: colors.labelAssistive, borderWidth: 0 },
  circleNow:  { backgroundColor: colors.primaryBase,    borderWidth: 0 },
});
