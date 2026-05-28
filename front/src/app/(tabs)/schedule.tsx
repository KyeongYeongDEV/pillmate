import React, { useCallback } from 'react';
import { View, Text, ScrollView, StyleSheet, Pressable } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { Feather } from '@expo/vector-icons';
import CalendarGrid from '@/components/schedule/CalendarGrid';
import MedTimeRow from '@/components/schedule/MedTimeRow';
import { colors, typography, space, radius } from '@/styles/tokens';
import { useGetDayScheduleQuery } from '@/store/slices/scheduleApi';
import { useCheckDoseMutation } from '@/store/slices/doseLogApi';
import { useAppSelector } from '@/store/hooks';
import type { RootState } from '@/store';
import type { MedSlot } from '@/types/schedule';

const LEGEND: [string, string][] = [
  ['전체 복용', colors.statusPositive],
  ['일부 미복용', colors.statusCautionary],
  ['미복용', colors.statusNegative],
];

const TODAY = new Date().toISOString().slice(0, 10);

export default function ScheduleScreen() {
  const { data: scheduleDay } = useGetDayScheduleQuery(TODAY);
  const [checkDose] = useCheckDoseMutation();
  const doseStateMap = useAppSelector((state: RootState) => state.doseState);

  const slots: MedSlot[] = scheduleDay?.slots ?? [];
  const displaySlots = slots.map(s => ({
    ...s,
    state: s.doseLogId != null ? (doseStateMap[s.doseLogId] ?? s.state) : s.state,
  }));

  const handleAdd = useCallback(() => { /* Phase 2: navigate to prescription upload */ }, []);

  const handleSlotPress = useCallback(async (slot: MedSlot) => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    if (!slot.doseLogId) return;
    const current = slot.doseLogId != null ? (doseStateMap[slot.doseLogId] ?? slot.state) : slot.state;
    const action = current === 'done' ? 'SKIP' : 'TAKE';
    checkDose({ doseLogId: slot.doseLogId, action });
  }, [doseStateMap, checkDose]);

  const doneCount = displaySlots.filter(s => s.state === 'done').length;

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>복약 스케줄</Text>
        <Pressable onPress={handleAdd} accessibilityLabel="처방전 추가" accessibilityRole="button">
          <Feather name="plus" size={24} color={colors.labelNormal} />
        </Pressable>
      </View>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.monthRow}>
          <Text style={styles.monthTitle}>2025년 11월</Text>
          <View style={styles.chevrons}>
            <Pressable style={styles.chevronBtn} accessibilityLabel="이전 달" accessibilityRole="button">
              <Feather name="chevron-left" size={18} color={colors.labelNormal} />
            </Pressable>
            <Pressable style={styles.chevronBtn} accessibilityLabel="다음 달" accessibilityRole="button">
              <Feather name="chevron-right" size={18} color={colors.labelNormal} />
            </Pressable>
          </View>
        </View>

        <CalendarGrid />

        <View style={styles.legend}>
          {LEGEND.map(([label, color]) => (
            <View key={label} style={styles.legendItem}>
              <View style={[styles.legendDot, { backgroundColor: color }]} />
              <Text style={styles.legendText}>{label}</Text>
            </View>
          ))}
        </View>

        <View style={styles.separator} />
        <View style={styles.todayArea}>
          <View style={styles.todayHeader}>
            <Text style={styles.todayLabel}>오늘 · 11월 24일 월</Text>
            <Text style={styles.todayCount}>복약 {doneCount} / {slots.length} 완료</Text>
          </View>
          <View style={styles.medCard}>
            {displaySlots.map((slot, i) => (
              <MedTimeRow
                key={slot.id}
                slot={slot}
                isFirst={i === 0}
                onPress={handleSlotPress}
              />
            ))}
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgNormal },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  scroll: { flex: 1 },
  content: { paddingBottom: 80 },
  monthRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s16,
  },
  monthTitle: { fontSize: 22, fontWeight: '700', letterSpacing: -0.018, color: colors.labelNormal },
  chevrons: { flexDirection: 'row', gap: space.s4 },
  chevronBtn: {
    width: 32, height: 32, borderRadius: radius.r8,
    backgroundColor: colors.fillNormal, alignItems: 'center', justifyContent: 'center',
  },
  legend: { flexDirection: 'row', gap: space.s16, paddingHorizontal: space.s16, marginTop: space.s14, marginBottom: space.s12 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  legendDot: { width: 6, height: 6, borderRadius: 3 },
  legendText: { fontSize: 12, color: colors.labelAlternative },
  separator: { height: 8, backgroundColor: colors.bgAlt },
  todayArea: { backgroundColor: colors.bgAlt, paddingBottom: space.s24 },
  todayHeader: { paddingHorizontal: space.s16, paddingTop: space.s20, paddingBottom: space.s12 },
  todayLabel: { fontSize: 12, color: colors.labelAlternative, fontWeight: '600', letterSpacing: 0.04 },
  todayCount: { ...typography.headline1, marginTop: 2, color: colors.labelNormal },
  medCard: {
    marginHorizontal: space.s16, backgroundColor: colors.bgNormal,
    borderRadius: radius.r16, borderWidth: 1, borderColor: colors.line, overflow: 'hidden',
  },
});
