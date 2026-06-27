import React, { useEffect, useState, useCallback } from 'react';
import { View, Text, TextInput, Pressable, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import { MIN_DURATION_DAYS, MAX_DURATION_DAYS } from '@/lib/constants';

const QUICK_DAYS = [3, 7, 14, 30];

interface Props {
  valueDays: number | null; // null = 무기한
  onChange: (days: number | null) => void;
}

export function clampDuration(days: number): number {
  if (Number.isNaN(days)) return MIN_DURATION_DAYS;
  return Math.min(MAX_DURATION_DAYS, Math.max(MIN_DURATION_DAYS, Math.floor(days)));
}

function DurationField({ valueDays, onChange }: Props) {
  const indefinite = valueDays === null;
  const [text, setText] = useState(indefinite ? '' : String(valueDays));

  useEffect(() => {
    setText(valueDays === null ? '' : String(valueDays));
  }, [valueDays]);

  const handleText = useCallback((raw: string) => {
    const digits = raw.replace(/[^0-9]/g, '');
    setText(digits);
    if (digits !== '') onChange(clampDuration(Number(digits)));
  }, [onChange]);

  const toggleIndefinite = useCallback(() => {
    onChange(indefinite ? MIN_DURATION_DAYS : null);
  }, [indefinite, onChange]);

  return (
    <View style={styles.wrap}>
      <View style={styles.inputRow}>
        <TextInput
          style={[styles.input, indefinite && styles.inputDisabled]}
          value={text}
          onChangeText={handleText}
          editable={!indefinite}
          keyboardType="number-pad"
          returnKeyType="done"
          maxLength={3}
          placeholder="예: 5"
          placeholderTextColor={colors.labelAssistive}
          accessibilityLabel="복약 일수 입력"
        />
        <Text style={styles.dayUnit}>일</Text>

        <Pressable
          style={styles.checkbox}
          onPress={toggleIndefinite}
          accessibilityRole="checkbox"
          accessibilityState={{ checked: indefinite }}
          accessibilityLabel="무기한"
        >
          <View style={[styles.checkboxBox, indefinite && styles.checkboxBoxOn]}>
            {indefinite && <Feather name="check" size={scale(13)} color={colors.staticWhite} />}
          </View>
          <Text style={styles.checkboxLabel}>무기한</Text>
        </Pressable>
      </View>

      <View style={styles.quickRow}>
        {QUICK_DAYS.map(days => (
          <Pressable
            key={days}
            style={[styles.quickChip, !indefinite && valueDays === days && styles.quickChipOn]}
            onPress={() => onChange(days)}
            accessibilityLabel={`${days}일`}
            accessibilityRole="button"
          >
            <Text style={[styles.quickTxt, !indefinite && valueDays === days && styles.quickTxtOn]}>
              {days}일
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

export default React.memo(DurationField);

const styles = StyleSheet.create({
  wrap: { gap: space.s10 },
  inputRow: { flexDirection: 'row', alignItems: 'center', gap: space.s8 },
  input: {
    width: scale(72), height: scale(44),
    borderWidth: 1, borderColor: colors.line, borderRadius: radius.r12,
    paddingHorizontal: space.s12, paddingVertical: 0,
    textAlign: 'center', textAlignVertical: 'center', includeFontPadding: false,
    ...typography.body2n, color: colors.labelNormal, backgroundColor: colors.bgNormal,
  },
  inputDisabled: { backgroundColor: colors.bgAlt, color: colors.labelAssistive },
  dayUnit: { ...typography.body2n, color: colors.labelNormal },
  checkbox: { flexDirection: 'row', alignItems: 'center', gap: space.s6, marginLeft: 'auto' },
  checkboxBox: {
    width: scale(22), height: scale(22), borderRadius: radius.r6,
    borderWidth: 1.5, borderColor: colors.line,
    alignItems: 'center', justifyContent: 'center',
  },
  checkboxBoxOn: { backgroundColor: colors.primaryNormal, borderColor: colors.primaryNormal },
  checkboxLabel: { ...typography.body2n, color: colors.labelNormal },
  quickRow: { flexDirection: 'row', flexWrap: 'wrap', gap: space.s8 },
  quickChip: {
    paddingHorizontal: space.s12, paddingVertical: space.s8,
    borderRadius: radius.r8, backgroundColor: colors.bgAlt,
    borderWidth: 1, borderColor: colors.line,
  },
  quickChipOn: { backgroundColor: colors.labelNormal, borderColor: colors.labelNormal },
  quickTxt: { ...typography.label2, color: colors.labelNeutral },
  quickTxtOn: { color: colors.staticWhite, fontWeight: '700' },
});
