import React, { memo, useCallback } from 'react';
import { View, Pressable, Text, StyleSheet } from 'react-native';
import { scale, colors, typography, space, radius } from '@/styles/tokens';

interface Props {
  value: number;
  unit: string;
  min?: number;
  max?: number;
  onChange: (value: number) => void;
}

function DoseStepper({ value, unit, min = 1, max = 10, onChange }: Props) {
  const decrement = useCallback(() => {
    if (value > min) onChange(value - 1);
  }, [value, min, onChange]);

  const increment = useCallback(() => {
    if (value < max) onChange(value + 1);
  }, [value, max, onChange]);

  return (
    <View style={styles.container}>
      <Pressable
        onPress={decrement}
        disabled={value <= min}
        style={[styles.btn, value <= min && styles.btnDisabled]}
        accessibilityLabel="복용량 줄이기"
        accessibilityRole="button"
      >
        <Text style={styles.btnText}>−</Text>
      </Pressable>
      <View style={styles.valueBox}>
        <Text style={styles.value}>{value}</Text>
        <Text style={styles.unit}>{unit}</Text>
      </View>
      <Pressable
        onPress={increment}
        disabled={value >= max}
        style={[styles.btn, value >= max && styles.btnDisabled]}
        accessibilityLabel="복용량 늘리기"
        accessibilityRole="button"
      >
        <Text style={styles.btnText}>+</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.s8,
    backgroundColor: colors.bgAlt,
    borderRadius: radius.r12,
    padding: space.s4,
  },
  btn: {
    width: scale(36),
    height: scale(36),
    borderRadius: radius.r8,
    backgroundColor: colors.bgNormal,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.line,
  },
  btnDisabled: { opacity: 0.4 },
  btnText: { ...typography.heading2, color: colors.labelNeutral },
  valueBox: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.s4 },
  value: { ...typography.headline1, color: colors.labelNormal },
  unit:  { ...typography.body2n, color: colors.labelAlternative },
});

export default memo(DoseStepper);
