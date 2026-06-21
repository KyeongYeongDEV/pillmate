import React, { useCallback } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import Icon from '@/components/common/Icon';
import { scale, colors, space } from '@/styles/tokens';
import { MFDS_SOURCE } from '@/lib/constants';
import type { MedSlot } from '@/types/schedule';

interface Props {
  slot: MedSlot;
  isFirst?: boolean;
  onPress?: (slot: MedSlot) => void;
  onPrescriptionPress?: (slot: MedSlot) => void;
  readOnly?: boolean;
}

function MedTimeRow({ slot, isFirst, onPress, onPrescriptionPress, readOnly }: Props) {
  const done = slot.state === 'done';
  const now  = !readOnly && slot.state === 'now';
  const handleCheckPress  = useCallback(() => onPress?.(slot), [onPress, slot]);
  const handlePrescriptionPress = useCallback(
    () => onPrescriptionPress?.(slot),
    [onPrescriptionPress, slot],
  );
  const canNavigate = !!slot.prescriptionId && !!onPrescriptionPress;

  const circleEl = (
    <View
      testID="dose-circle"
      style={[
        styles.circle,
        done && (readOnly ? styles.circleDoneReadOnly : styles.circleDone),
        now && styles.circleNow,
      ]}
    >
      {done && <Icon name="check" size={scale(18)} color="#fff" />}
    </View>
  );

  return (
    <View style={[styles.row, !isFirst && styles.borderTop]}>
      <View style={styles.timeCol}>
        <Text style={[styles.time, done && styles.muted]}>{slot.time}</Text>
        <Text style={styles.timeLabel}>{slot.label}</Text>
      </View>
      <View style={styles.divider} />
      <View style={styles.itemsCol}>
        {slot.prescriptionName ? (
          canNavigate ? (
            <Pressable onPress={handlePrescriptionPress} accessibilityRole="link">
              <Text
                style={[styles.item, done && styles.muted, done && styles.strike]}
                numberOfLines={1}
              >
                {slot.prescriptionName}
              </Text>
            </Pressable>
          ) : (
            <Text style={[styles.item, done && styles.muted, done && styles.strike]}>
              {slot.prescriptionName}
            </Text>
          )
        ) : (
          slot.items.map((it, i) => (
            <Text key={i} style={[styles.item, done && styles.muted, done && styles.strike]}>{it}</Text>
          ))
        )}
        <Text style={styles.source}>출처: {MFDS_SOURCE}</Text>
      </View>
      {onPress ? (
        <Pressable
          testID="dose-circle"
          style={[
            styles.circle,
            done && (readOnly ? styles.circleDoneReadOnly : styles.circleDone),
            now && styles.circleNow,
          ]}
          onPress={handleCheckPress}
          accessibilityLabel={`${slot.label} ${slot.time} 복약 체크`}
          accessibilityRole="button"
        >
          {done && <Icon name="check" size={scale(18)} color="#fff" />}
        </Pressable>
      ) : (
        circleEl
      )}
    </View>
  );
}

export default React.memo(MedTimeRow);

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row', alignItems: 'center',
    gap: space.s14, paddingVertical: space.s14, paddingHorizontal: space.s16,
  },
  borderTop: { borderTopWidth: 1, borderTopColor: colors.line },
  timeCol: { width: scale(48), alignItems: 'center' },
  time: { fontSize: scale(15), fontWeight: '700', color: colors.labelNormal },
  timeLabel: { fontSize: scale(11), color: colors.labelAlternative, marginTop: 1 },
  muted: { color: colors.labelAlternative },
  strike: { textDecorationLine: 'line-through' },
  divider: { width: scale(1), height: scale(36), backgroundColor: colors.line },
  itemsCol: { flex: 1, gap: 1 },
  item: { fontSize: scale(14), fontWeight: '500', color: colors.labelNormal, lineHeight: scale(20) },
  source: { fontSize: scale(10), color: colors.labelAssistive, marginTop: 3 },
  circle: {
    width: scale(32), height: scale(32), borderRadius: scale(16),
    borderWidth: 1.5, borderColor: colors.line,
    alignItems: 'center', justifyContent: 'center',
  },
  circleDone: { backgroundColor: colors.statusPositive, borderWidth: 0 },
  circleDoneReadOnly: { backgroundColor: colors.labelAssistive, borderWidth: 0 },
  circleNow:  { backgroundColor: colors.primaryBase,    borderWidth: 0 },
});
