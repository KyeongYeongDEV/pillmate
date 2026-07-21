import React, { useCallback, useMemo, useState } from 'react';
import { View, Text, ScrollView, StyleSheet, Pressable, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import CalendarGrid from '@/components/schedule/CalendarGrid';
import MonthPicker from '@/components/schedule/MonthPicker';
import MedTimeRow from '@/components/schedule/MedTimeRow';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import TabHeader from '@/components/navigation/TabHeader';
import { useGetDayScheduleQuery, useGetMonthAdherenceQuery } from '@/store/slices/scheduleApi';
import { useAppSelector } from '@/store/hooks';
import { useSlotPress } from '@/hooks/useSlotPress';
import {
  prevMonth, nextMonth, toMonthString, formatDayLabel, deriveAdherence,
  getKstToday, isEditableDate,
} from '@/utils/calendarUtils';
import { deriveOverlayState } from '@/lib/scheduleUtils';
import { useKstToday } from '@/hooks/useKstToday';
import type { RootState } from '@/store';
import type { MedSlot } from '@/types/schedule';

const LEGEND: [string, string][] = [
  ['전체 복용', colors.statusPositive],
  ['일부 미복용', colors.statusCautionary],
  ['미복용', colors.statusNegative],
];

const PAST_DATE_LOCKED_MSG = '지난 날짜의 복약 기록은 수정할 수 없어요';
const FUTURE_DATE_LOCKED_MSG = '아직 체크할 수 없어요';

function alertDateLocked(selectedDate: string, today: string) {
  Alert.alert(selectedDate < today ? PAST_DATE_LOCKED_MSG : FUTURE_DATE_LOCKED_MSG);
}

export default function ScheduleScreen() {
  const today = useKstToday();

  const [displayYear, setDisplayYear] = useState(() => Number(getKstToday().slice(0, 4)));
  const [displayMonth, setDisplayMonth] = useState(() => Number(getKstToday().slice(5, 7)));
  const [selectedDate, setSelectedDate] = useState(() => getKstToday());
  const [pickerVisible, setPickerVisible] = useState(false);

  const { data: scheduleDay } = useGetDayScheduleQuery(selectedDate);
  const { data: monthAdherence } = useGetMonthAdherenceQuery(toMonthString(displayYear, displayMonth));
  const doseStateMap = useAppSelector((state: RootState) => state.doseState);
  const pressSlot = useSlotPress();

  const rawSlots: MedSlot[] = scheduleDay?.slots ?? [];
  const displaySlots = useMemo(
    () => rawSlots.map(s => {
      const ids = s.doseLogIds?.length ? s.doseLogIds : s.doseLogId != null ? [s.doseLogId] : [];
      const state = deriveOverlayState(ids, s.state, doseStateMap);
      return { ...s, state };
    }),
    [rawSlots, doseStateMap],
  );

  const adherenceByDate = useMemo(() => {
    const base = monthAdherence ?? {};
    if (selectedDate !== today || !scheduleDay) return base;
    const todayLevel = deriveAdherence(displaySlots);
    return todayLevel ? { ...base, [today]: todayLevel } : base;
  }, [monthAdherence, scheduleDay, displaySlots, selectedDate, today]);

  const handleSlotPress = useMemo(
    () => (slot: MedSlot) => {
      if (!isEditableDate(selectedDate, new Date())) {
        alertDateLocked(selectedDate, getKstToday());
        return;
      }
      const ids = slot.doseLogIds?.length
        ? slot.doseLogIds
        : slot.doseLogId != null ? [slot.doseLogId] : [];
      pressSlot(ids, slot.state);
    },
    [pressSlot, selectedDate],
  );

  const handlePrescriptionPress = useMemo(
    () => (slot: MedSlot) => {
      if (slot.prescriptionId) {
        router.push({ pathname: '/prescription/[id]', params: { id: String(slot.prescriptionId) } } as any);
      }
    },
    [],
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

  const editable = isEditableDate(selectedDate, new Date());
  const doneCount = displaySlots.filter(s => s.state === 'done').length;
  const dayLabel = formatDayLabel(selectedDate, today);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <TabHeader title="복약 스케줄" />

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
              <Feather name="chevron-left" size={scale(18)} color={colors.labelNormal} />
            </Pressable>
            <Pressable
              style={styles.chevronBtn}
              onPress={handleNextMonth}
              accessibilityLabel="다음 달"
              accessibilityRole="button"
            >
              <Feather name="chevron-right" size={scale(18)} color={colors.labelNormal} />
            </Pressable>
          </View>
        </View>

        <CalendarGrid
          year={displayYear}
          month={displayMonth}
          selectedDate={selectedDate}
          today={today}
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
                key={`${slot.id}-${i}`}
                slot={slot}
                isFirst={i === 0}
                onPress={handleSlotPress}
                onPrescriptionPress={handlePrescriptionPress}
                readOnly={!editable}
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
  scroll: { flex: 1 },
  content: { paddingBottom: 80 },
  monthRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s16,
  },
  monthTitle: { fontSize: scale(22), fontWeight: '700', letterSpacing: -0.018, color: colors.labelNormal },
  chevrons: { flexDirection: 'row', gap: space.s4 },
  chevronBtn: {
    width: scale(32), height: scale(32), borderRadius: radius.r8,
    backgroundColor: colors.fillNormal, alignItems: 'center', justifyContent: 'center',
  },
  legend: { flexDirection: 'row', gap: space.s16, paddingHorizontal: space.s16, marginTop: space.s14, marginBottom: space.s12 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  legendDot: { width: scale(6), height: scale(6), borderRadius: scale(3) },
  legendText: { fontSize: scale(12), color: colors.labelAlternative },
  separator: { height: scale(8), backgroundColor: colors.bgAlt },
  todayArea: { backgroundColor: colors.bgAlt, paddingBottom: space.s24 },
  todayHeader: { paddingHorizontal: space.s16, paddingTop: space.s20, paddingBottom: space.s12 },
  todayLabel: { fontSize: scale(12), color: colors.labelAlternative, fontWeight: '600', letterSpacing: 0.04 },
  todayCount: { ...typography.headline1, marginTop: 2, color: colors.labelNormal },
  medCard: {
    marginHorizontal: space.s16, backgroundColor: colors.bgNormal,
    borderRadius: radius.r16, borderWidth: 1, borderColor: colors.line, overflow: 'hidden',
  },
});
