import React, { useState } from 'react';
import {
  Modal, View, Text, Pressable, StyleSheet, TextInput, KeyboardAvoidingView, Platform,
} from 'react-native';
import { scale, colors, space, radius } from '@/styles/tokens';
import { useSheetBottomPadding } from '@/hooks/useSheetBottomPadding';

const HOUR_MAX = 23;
const MINUTE_MAX = 59;

function pad(n: number): string {
  return n.toString().padStart(2, '0');
}

function clamp(n: number, max: number): number {
  return Math.min(max, Math.max(0, n));
}

function parseTime(time: string): { hour: number; minute: number } {
  const [h, m] = time.split(':').map(Number);
  return {
    hour: clamp(Number.isFinite(h) ? h : 8, HOUR_MAX),
    minute: clamp(Number.isFinite(m) ? m : 0, MINUTE_MAX),
  };
}

export function formatTimeHHmm(hour: number, minute: number): string {
  return `${pad(hour)}:${pad(minute)}`;
}

export function formatTimeHHmmss(hour: number, minute: number): string {
  return `${pad(hour)}:${pad(minute)}:00`;
}

export interface TimePickerProps {
  visible: boolean;
  initialTime: string;
  onConfirm: (time: string) => void;
  onClose: () => void;
}

export default function TimePicker({ visible, initialTime, onConfirm, onClose }: TimePickerProps) {
  const [hourText, setHourText] = useState(() => pad(parseTime(initialTime).hour));
  const [minuteText, setMinuteText] = useState(() => pad(parseTime(initialTime).minute));
  const sheetBottom = useSheetBottomPadding();

  const handleShow = () => {
    const parsed = parseTime(initialTime);
    setHourText(pad(parsed.hour));
    setMinuteText(pad(parsed.minute));
  };

  const hourNum = clamp(parseInt(hourText, 10) || 0, HOUR_MAX);
  const minuteNum = clamp(parseInt(minuteText, 10) || 0, MINUTE_MAX);

  const onlyDigits = (text: string) => text.replace(/[^0-9]/g, '').slice(0, 2);
  const stepHour = (delta: number) => setHourText(pad((hourNum + delta + 24) % 24));
  const stepMinute = (delta: number) => setMinuteText(pad((minuteNum + delta + 60) % 60));

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onShow={handleShow}
      onRequestClose={onClose}
    >
      <Pressable style={styles.backdrop} onPress={onClose} accessibilityLabel="닫기" />
      <KeyboardAvoidingView
        style={styles.kav}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        pointerEvents="box-none"
      >
        <View style={[styles.sheet, { paddingBottom: sheetBottom }]}>
          <Text style={styles.title}>알림 시간 변경</Text>

          <View style={styles.pickerRow}>
            <View style={styles.stepperGroup}>
              <Pressable style={styles.stepBtn} onPress={() => stepHour(-1)} accessibilityLabel="이전 시" accessibilityRole="button">
                <Text style={styles.stepArrow}>‹</Text>
              </Pressable>
              <TextInput
                style={styles.stepValue}
                value={hourText}
                onChangeText={(t) => setHourText(onlyDigits(t))}
                onBlur={() => setHourText(pad(hourNum))}
                onEndEditing={() => setHourText(pad(hourNum))}
                keyboardType="number-pad"
                maxLength={2}
                selectTextOnFocus
                textAlign="center"
                accessibilityLabel="시 입력"
              />
              <Pressable style={styles.stepBtn} onPress={() => stepHour(1)} accessibilityLabel="다음 시" accessibilityRole="button">
                <Text style={styles.stepArrow}>›</Text>
              </Pressable>
            </View>

            <Text style={styles.colon}>:</Text>

            <View style={styles.stepperGroup}>
              <Pressable style={styles.stepBtn} onPress={() => stepMinute(-1)} accessibilityLabel="이전 분" accessibilityRole="button">
                <Text style={styles.stepArrow}>‹</Text>
              </Pressable>
              <TextInput
                style={styles.stepValue}
                value={minuteText}
                onChangeText={(t) => setMinuteText(onlyDigits(t))}
                onBlur={() => setMinuteText(pad(minuteNum))}
                onEndEditing={() => setMinuteText(pad(minuteNum))}
                keyboardType="number-pad"
                maxLength={2}
                selectTextOnFocus
                textAlign="center"
                accessibilityLabel="분 입력"
              />
              <Pressable style={styles.stepBtn} onPress={() => stepMinute(1)} accessibilityLabel="다음 분" accessibilityRole="button">
                <Text style={styles.stepArrow}>›</Text>
              </Pressable>
            </View>
          </View>

          <Text style={styles.hint}>시·분 직접 입력 (0~23 / 0~59)</Text>

          <View style={styles.footer}>
            <Pressable style={styles.cancelBtn} onPress={onClose} accessibilityRole="button">
              <Text style={styles.cancelTxt}>취소</Text>
            </Pressable>
            <Pressable
              style={styles.confirmBtn}
              onPress={() => onConfirm(formatTimeHHmmss(hourNum, minuteNum))}
              accessibilityRole="button"
              accessibilityLabel="확인"
            >
              <Text style={styles.confirmTxt}>확인</Text>
            </Pressable>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.45)',
  },
  kav: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: colors.bgNormal,
    borderTopLeftRadius: 20, borderTopRightRadius: 20,
    paddingTop: space.s24,
    paddingHorizontal: space.s24,
  },
  title: {
    fontSize: scale(17), fontWeight: '700', color: colors.labelNormal,
    textAlign: 'center', marginBottom: space.s28,
  },
  pickerRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
    gap: space.s8,
  },
  stepperGroup: {
    flexDirection: 'row', alignItems: 'center', gap: space.s16,
    backgroundColor: colors.bgAlt, borderRadius: radius.r16,
    paddingVertical: space.s12, paddingHorizontal: space.s16,
  },
  stepBtn: { padding: space.s4 },
  stepArrow: {
    fontSize: scale(28), fontWeight: '300', color: colors.primaryNormal, lineHeight: scale(32),
  },
  stepValue: {
    fontSize: scale(36), fontWeight: '700', color: colors.labelNormal,
    minWidth: scale(60), textAlign: 'center', padding: 0,
  },
  colon: {
    fontSize: scale(36), fontWeight: '700', color: colors.labelNormal,
    marginBottom: scale(2),
  },
  hint: {
    fontSize: scale(12), color: colors.labelAssistive, textAlign: 'center',
    marginTop: space.s12,
  },
  footer: {
    flexDirection: 'row', gap: space.s10, marginTop: space.s28,
  },
  cancelBtn: {
    flex: 1, paddingVertical: space.s14,
    borderRadius: radius.r12,
    backgroundColor: colors.bgAlt, borderWidth: 1, borderColor: colors.line,
    alignItems: 'center',
  },
  cancelTxt: { fontSize: scale(15), fontWeight: '600', color: colors.labelNormal },
  confirmBtn: {
    flex: 2, paddingVertical: space.s14,
    borderRadius: radius.r12, backgroundColor: colors.primaryNormal,
    alignItems: 'center',
  },
  confirmTxt: { fontSize: scale(15), fontWeight: '700', color: '#fff' },
});
