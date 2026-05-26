import React, { memo, useCallback } from 'react';
import { View, Pressable, Text, StyleSheet } from 'react-native';
import type { DrugSlots } from '@/types/prescription';
import { colors, typography, space, radius } from '@/styles/tokens';

interface Props {
  slots: DrugSlots;
  onChange: (slots: DrugSlots) => void;
}

const SLOT_LABELS: { key: keyof DrugSlots; label: string; time: string }[] = [
  { key: 'morning', label: '아침', time: '08:00' },
  { key: 'noon',    label: '점심', time: '12:30' },
  { key: 'evening', label: '저녁', time: '19:00' },
  { key: 'bedtime', label: '취침전', time: '22:00' },
];

function SlotToggle({ slots, onChange }: Props) {
  const toggle = useCallback(
    (key: keyof DrugSlots) => {
      onChange({ ...slots, [key]: !slots[key] });
    },
    [slots, onChange],
  );

  return (
    <View style={styles.container}>
      {SLOT_LABELS.map(({ key, label, time }) => {
        const on = slots[key];
        return (
          <Pressable
            key={key}
            onPress={() => toggle(key)}
            style={[styles.slot, on ? styles.slotOn : styles.slotOff]}
            accessibilityRole="checkbox"
            accessibilityLabel={`${label} 복용`}
            accessibilityState={{ checked: on }}
          >
            <Text style={[styles.slotLabel, on ? styles.textOn : styles.textOff]}>{label}</Text>
            <Text style={[styles.slotTime, on ? styles.timeOn : styles.timeOff]}>
              {on ? time : '—'}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    gap: space.s6,
    backgroundColor: colors.bgAlt,
    borderRadius: radius.r10,
    padding: space.s8,
  },
  slot: {
    flex: 1,
    borderRadius: radius.r8,
    paddingVertical: space.s8,
    alignItems: 'center',
    justifyContent: 'center',
    gap: space.s2,
  },
  slotOn:  { backgroundColor: colors.primaryNormal },
  slotOff: { backgroundColor: colors.bgNormal, borderWidth: 1, borderColor: colors.line },
  slotLabel: { ...typography.label2 },
  textOn:  { color: '#fff', fontWeight: '700' },
  textOff: { color: colors.labelAlternative },
  slotTime: { ...typography.caption1 },
  timeOn:  { color: 'rgba(255,255,255,0.8)' },
  timeOff: { color: colors.labelAssistive },
});

export default memo(SlotToggle);
