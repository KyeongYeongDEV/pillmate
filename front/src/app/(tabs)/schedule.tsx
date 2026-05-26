import React, { useCallback } from 'react';
import { View, Text, ScrollView, StyleSheet, Pressable } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import CalendarGrid from '@/components/schedule/CalendarGrid';
import MedTimeRow from '@/components/schedule/MedTimeRow';
import { colors, typography, space, radius } from '@/styles/tokens';
import type { MedSlot } from '@/types/schedule';

const MOCK_SLOTS: MedSlot[] = [
  { id: 'morning', time: '08:00', label: '아침',    state: 'done', items: ['암로디핀 5mg', '메트포르민 500mg'] },
  { id: 'noon',    time: '12:30', label: '점심',    state: 'now',  items: ['메트포르민 500mg', '글리메피리드 2mg'] },
  { id: 'evening', time: '19:00', label: '저녁',    state: 'wait', items: ['아토르바스타틴 10mg'] },
  { id: 'bedtime', time: '22:00', label: '취침 전', state: 'wait', items: ['오메가-3 1000mg'] },
];

const LEGEND: [string, string][] = [
  ['전체 복용', colors.statusPositive],
  ['일부 미복용', colors.statusCautionary],
  ['미복용', colors.statusNegative],
];

export default function ScheduleScreen() {
  const handleAdd = useCallback(() => { /* Phase 2: navigate to prescription upload */ }, []);

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
            <Text style={styles.todayCount}>복약 4 / 6 완료</Text>
          </View>
          <View style={styles.medCard}>
            {MOCK_SLOTS.map((slot, i) => (
              <MedTimeRow key={slot.id} slot={slot} isFirst={i === 0} />
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
