import React, { useState } from 'react';
import {
  Modal, View, Text, Pressable, StyleSheet,
} from 'react-native';
import { colors, space, radius } from '@/styles/tokens';

const MONTHS = ['1월', '2월', '3월', '4월', '5월', '6월',
                '7월', '8월', '9월', '10월', '11월', '12월'];

const YEAR_MIN = 2020;
const YEAR_MAX = 2030;

export interface MonthPickerProps {
  visible: boolean;
  year: number;
  month: number;
  onConfirm: (year: number, month: number) => void;
  onClose: () => void;
}

export default function MonthPicker({ visible, year, month, onConfirm, onClose }: MonthPickerProps) {
  const [pickerYear, setPickerYear] = useState(year);
  const [pickerMonth, setPickerMonth] = useState(month);

  const handleOpen = () => { setPickerYear(year); setPickerMonth(month); };

  const decrementYear = () => setPickerYear(y => Math.max(YEAR_MIN, y - 1));
  const incrementYear = () => setPickerYear(y => Math.min(YEAR_MAX, y + 1));

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onShow={handleOpen}
      onRequestClose={onClose}
    >
      <Pressable style={styles.backdrop} onPress={onClose} accessibilityLabel="닫기" />
      <View style={styles.sheet}>
        <View style={styles.yearRow}>
          <Pressable
            onPress={decrementYear}
            style={styles.yearBtn}
            disabled={pickerYear <= YEAR_MIN}
            accessibilityLabel="이전 연도"
            accessibilityRole="button"
          >
            <Text style={[styles.yearArrow, pickerYear <= YEAR_MIN && styles.arrowDisabled]}>‹</Text>
          </Pressable>
          <Text style={styles.yearLabel}>{pickerYear}년</Text>
          <Pressable
            onPress={incrementYear}
            style={styles.yearBtn}
            disabled={pickerYear >= YEAR_MAX}
            accessibilityLabel="다음 연도"
            accessibilityRole="button"
          >
            <Text style={[styles.yearArrow, pickerYear >= YEAR_MAX && styles.arrowDisabled]}>›</Text>
          </Pressable>
        </View>

        <View style={styles.monthGrid}>
          {MONTHS.map((label, i) => {
            const m = i + 1;
            const isSelected = m === pickerMonth && pickerYear === year;
            return (
              <Pressable
                key={m}
                style={[styles.monthCell, isSelected && styles.monthCellSelected]}
                onPress={() => setPickerMonth(m)}
                accessibilityLabel={label}
                accessibilityState={{ selected: isSelected }}
                accessibilityRole="button"
              >
                <Text style={[styles.monthText, isSelected && styles.monthTextSelected]}>
                  {label}
                </Text>
              </Pressable>
            );
          })}
        </View>

        <View style={styles.footer}>
          <Pressable onPress={onClose} style={styles.footerBtnCancel} accessibilityRole="button">
            <Text style={styles.footerCancelTxt}>취소</Text>
          </Pressable>
          <Pressable
            onPress={() => onConfirm(pickerYear, pickerMonth)}
            style={styles.footerBtnConfirm}
            accessibilityRole="button"
            accessibilityLabel="확인"
          >
            <Text style={styles.footerConfirmTxt}>확인</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.45)',
  },
  sheet: {
    position: 'absolute',
    bottom: 0, left: 0, right: 0,
    backgroundColor: colors.bgNormal,
    borderTopLeftRadius: 20, borderTopRightRadius: 20,
    paddingTop: space.s20, paddingBottom: space.s40,
    paddingHorizontal: space.s20,
  },
  yearRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
    gap: space.s24, marginBottom: space.s20,
  },
  yearBtn: { padding: space.s8 },
  yearArrow: { fontSize: 26, fontWeight: '300', color: colors.labelNormal, lineHeight: 30 },
  arrowDisabled: { color: colors.labelAssistive },
  yearLabel: { fontSize: 20, fontWeight: '700', color: colors.labelNormal, minWidth: 80, textAlign: 'center' },
  monthGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: space.s8 },
  monthCell: {
    width: '22%', paddingVertical: space.s12,
    borderRadius: radius.r12, alignItems: 'center',
    backgroundColor: colors.fillNormal,
  },
  monthCellSelected: { backgroundColor: colors.labelNormal },
  monthText: { fontSize: 14, fontWeight: '600', color: colors.labelNormal },
  monthTextSelected: { color: '#fff' },
  footer: {
    flexDirection: 'row', gap: space.s10,
    marginTop: space.s20,
  },
  footerBtnCancel: {
    flex: 1, paddingVertical: space.s14,
    borderRadius: radius.r12,
    backgroundColor: colors.bgAlt, borderWidth: 1, borderColor: colors.line,
    alignItems: 'center',
  },
  footerCancelTxt: { fontSize: 15, fontWeight: '600', color: colors.labelNormal },
  footerBtnConfirm: {
    flex: 2, paddingVertical: space.s14,
    borderRadius: radius.r12,
    backgroundColor: colors.primaryNormal,
    alignItems: 'center',
  },
  footerConfirmTxt: { fontSize: 15, fontWeight: '700', color: '#fff' },
});
