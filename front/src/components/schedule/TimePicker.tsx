import React, { useState } from 'react';
import { Modal, View, Text, Pressable, StyleSheet } from 'react-native';
import { scale, colors, space, radius } from '@/styles/tokens';

const MINUTE_STEP = 5;
const MINUTES = Array.from({ length: 60 / MINUTE_STEP }, (_, i) => i * MINUTE_STEP);

function pad(n: number): string {
  return n.toString().padStart(2, '0');
}

function parseTime(time: string): { hour: number; minute: number } {
  const [h, m] = time.split(':').map(Number);
  const minute = Math.round(m / MINUTE_STEP) * MINUTE_STEP % 60;
  return { hour: h ?? 8, minute };
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
  const [hour, setHour] = useState(() => parseTime(initialTime).hour);
  const [minute, setMinute] = useState(() => parseTime(initialTime).minute);

  const handleShow = () => {
    const parsed = parseTime(initialTime);
    setHour(parsed.hour);
    setMinute(parsed.minute);
  };

  const prevHour   = () => setHour(h => (h + 23) % 24);
  const nextHour   = () => setHour(h => (h + 1) % 24);
  const prevMinute = () => setMinute(m => {
    const idx = MINUTES.indexOf(m);
    return MINUTES[(idx + MINUTES.length - 1) % MINUTES.length];
  });
  const nextMinute = () => setMinute(m => {
    const idx = MINUTES.indexOf(m);
    return MINUTES[(idx + 1) % MINUTES.length];
  });

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onShow={handleShow}
      onRequestClose={onClose}
    >
      <Pressable style={styles.backdrop} onPress={onClose} accessibilityLabel="닫기" />
      <View style={styles.sheet}>
        <Text style={styles.title}>알림 시간 변경</Text>

        <View style={styles.pickerRow}>
          {/* Hour stepper */}
          <View style={styles.stepperGroup}>
            <Pressable
              style={styles.stepBtn}
              onPress={prevHour}
              accessibilityLabel="이전 시"
              accessibilityRole="button"
            >
              <Text style={styles.stepArrow}>‹</Text>
            </Pressable>
            <Text style={styles.stepValue}>{pad(hour)}</Text>
            <Pressable
              style={styles.stepBtn}
              onPress={nextHour}
              accessibilityLabel="다음 시"
              accessibilityRole="button"
            >
              <Text style={styles.stepArrow}>›</Text>
            </Pressable>
          </View>

          <Text style={styles.colon}>:</Text>

          {/* Minute stepper */}
          <View style={styles.stepperGroup}>
            <Pressable
              style={styles.stepBtn}
              onPress={prevMinute}
              accessibilityLabel="이전 분"
              accessibilityRole="button"
            >
              <Text style={styles.stepArrow}>‹</Text>
            </Pressable>
            <Text style={styles.stepValue}>{pad(minute)}</Text>
            <Pressable
              style={styles.stepBtn}
              onPress={nextMinute}
              accessibilityLabel="다음 분"
              accessibilityRole="button"
            >
              <Text style={styles.stepArrow}>›</Text>
            </Pressable>
          </View>
        </View>

        <Text style={styles.hint}>5분 단위로 조절</Text>

        <View style={styles.footer}>
          <Pressable
            style={styles.cancelBtn}
            onPress={onClose}
            accessibilityRole="button"
          >
            <Text style={styles.cancelTxt}>취소</Text>
          </Pressable>
          <Pressable
            style={styles.confirmBtn}
            onPress={() => onConfirm(formatTimeHHmmss(hour, minute))}
            accessibilityRole="button"
            accessibilityLabel="확인"
          >
            <Text style={styles.confirmTxt}>확인</Text>
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
    paddingTop: space.s24, paddingBottom: space.s40,
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
    minWidth: scale(52), textAlign: 'center',
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
