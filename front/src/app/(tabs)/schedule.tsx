import React, { useCallback, useMemo, useState } from 'react';
import { View, Text, ScrollView, StyleSheet, Pressable } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import CalendarGrid from '@/components/schedule/CalendarGrid';
import MonthPicker from '@/components/schedule/MonthPicker';
import MedTimeRow from '@/components/schedule/MedTimeRow';
import { colors, typography, space, radius } from '@/styles/tokens';
import { useGetDayScheduleQuery, useGetMonthAdherenceQuery } from '@/store/slices/scheduleApi';
import { useAppSelector } from '@/store/hooks';
import { useSlotPress } from '@/hooks/useSlotPress';
import { prevMonth, nextMonth, toMonthString, formatDayLabel, deriveAdherence } from '@/utils/calendarUtils';
import type { RootState } from '@/store';
import type { MedSlot, MedState } from '@/types/schedule';

const LEGEND: [string, string][] = [
  ['전체 복용', colors.statusPositive],
  ['일부 미복용', colors.statusCautionary],
  ['미복용', colors.statusNegative],
];

const TODAY = new Date().toISOString().slice(0, 10);

function parseTodayParts(): { year: number; month: number } {
  const [y, m] = TODAY.split('-').map(Number);
  return { year: y, month: m };
}

export default function ScheduleScreen() {
  const { year: todayYear, month: todayMonth } = parseTodayParts();

  const [displayYear, setDisplayYear] = useState(todayYear);
  const [displayMonth, setDisplayMonth] = useState(todayMonth);
  const [selectedDate, setSelectedDate] = useState(TODAY);
  const [pickerVisible, setPickerVisible] = useState(false);

  const { data: scheduleDay } = useGetDayScheduleQuery(selectedDate);
  const { data: monthAdherence } = useGetMonthAdherenceQuery(toMonthString(displayYear, displayMonth));
  const doseStateMap = useAppSelector((state: RootState) => state.doseState);
  const pressSlot = useSlotPress();

  const rawSlots: MedSlot[] = scheduleDay?.slots ?? [];
  const displaySlots = useMemo(
    () => rawSlots.map(s => {
      const entry = s.doseLogId != null ? doseStateMap[s.doseLogId] : undefined;
      return { ...s, state: (entry?.state ?? s.state) as MedState };
    }),
    [rawSlots, doseStateMap],
  );

  const adherenceByDate = useMemo(() => {
    const base = monthAdherence ?? {};
    if (selectedDate !== TODAY || !scheduleDay) return base;
    const todayLevel = deriveAdherence(displaySlots);
    return todayLevel ? { ...base, [TODAY]: todayLevel } : base;
  }, [monthAdherence, scheduleDay, displaySlots, selectedDate]);

  const handleSlotPress = useMemo(
    () => (slot: MedSlot) => pressSlot(slot.doseLogId, slot.state),
    [pressSlot],
  );

  const handlePrevMonth = useCallback(() => {
    const { year, month } = prevMonth(displayYear, displayMonth);
    setDisplayYear(year);
    setDisplayMonth(month);
  }, [displayYear, displayMonth]);

  const handleNextMonth = useCallback(() => {
    const { year, month } = nextMonth(displayYear, displayMonth);
    setDisplayYear(year);
    setDisplayMonth(month);
  }, [displayYear, displayMonth]);

  const handlePickerConfirm = useCallback((year: number, month: number) => {
    setDisplayYear(year);
    setDisplayMonth(month);
    setPickerVisible(false);
  }, []);

  const handleSelectDate = useCallback((date: string) => {
    setSelectedDate(date);
    const [y, m] = date.split('-').map(Number);
    setDisplayYear(y);
    setDisplayMonth(m);
  }, []);

  const doneCount = displaySlots.filter(s => s.state === 'done').length;
  const dayLabel = formatDayLabel(selectedDate, TODAY);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>복약 스케줄</Text>
      </View>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.monthRow}>
          <Pressable
            onPress={() => setPickerVisible(true)}
            accessibilityLabel="연월 선택"
            accessibilityRole="button"
          >
            <Text style={styles.monthTitle}>{displayYear}년 {displayMonth}월</Text>
          </Pressable>
          <View style={styles.chevrons}>
            <Pressable
              style={styles.chevronBtn}
              onPress={handlePrevMonth}
              accessibilityLabel="이전 달"
              accessibilityRole="button"
            >
              <Feather name="chevron-left" size={18} color={colors.labelNormal} />
            </Pressable>
            <Pressable
              style={styles.chevronBtn}
              onPress={handleNextMonth}
              accessibilityLabel="다음 달"
              accessibilityRole="button"
            >
              <Feather name="chevron-right" size={18} color={colors.labelNormal} />
            </Pressable>
          </View>
        </View>

        <CalendarGrid
          year={displayYear}
          month={displayMonth}
          selectedDate={selectedDate}
          today={TODAY}
          onSelectDate={handleSelectDate}
          adherenceByDate={adherenceByDate}
        />

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
            <Text style={styles.todayLabel}>{dayLabel}</Text>
            <Text style={styles.todayCount}>복약 {doneCount} / {displaySlots.length} 완료</Text>
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

      <MonthPicker
        visible={pickerVisible}
        year={displayYear}
        month={displayMonth}
        onConfirm={handlePickerConfirm}
        onClose={() => setPickerVisible(false)}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgNormal },
  header: {
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
