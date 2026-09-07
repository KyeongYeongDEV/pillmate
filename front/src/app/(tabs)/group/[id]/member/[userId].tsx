import React, { useCallback, useMemo, useState } from 'react';
import { View, Text, ScrollView, StyleSheet, Pressable } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import CalendarGrid from '@/components/schedule/CalendarGrid';
import MonthPicker from '@/components/schedule/MonthPicker';
import MedTimeRow from '@/components/schedule/MedTimeRow';
import BootSkeleton from '@/components/common/BootSkeleton';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import { safeBack } from '@/lib/router/safeBack';
import { useGetGroupDetailQuery } from '@/store/slices/caregroupApi';
import {
  useGetMemberDayScheduleQuery,
  useGetMemberMonthAdherenceQuery,
} from '@/store/slices/scheduleApi';
import { useKstToday } from '@/hooks/useKstToday';
import {
  prevMonth, nextMonth, toMonthString, formatDayLabel, getKstToday,
} from '@/utils/calendarUtils';
import type { MedSlot } from '@/types/schedule';

const LEGEND: [string, string][] = [
  ['전체 복용', colors.statusPositive],
  ['일부 미복용', colors.statusCautionary],
  ['미복용', colors.statusNegative],
  ['복용 예정', colors.primaryBase],
];

const FORBIDDEN_STATUS = 403;
const FORBIDDEN_MSG = '이 구성원의 복약 정보를 볼 수 없어요';
const LOAD_FAILED_MSG = '복약 정보를 불러올 수 없어요';
const RETRY_LABEL = '재시도';
const READ_ONLY_MSG = '보기 전용이에요. 복약 체크는 본인만 할 수 있어요';
const FALLBACK_MEMBER_NAME = '구성원';

function isForbidden(error: unknown): boolean {
  return (error as { status?: number } | undefined)?.status === FORBIDDEN_STATUS;
}

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

// 그룹 캘린더에서 특정 날짜를 눌러 진입한 경우 그 날짜를 초기 선택값으로 쓴다. 없거나 형식이 이상하면 오늘.
function resolveInitialDate(date?: string): string {
  return typeof date === 'string' && DATE_PATTERN.test(date) ? date : getKstToday();
}

export default function MemberScheduleScreen() {
  const { id, userId, name, date } = useLocalSearchParams<{
    id: string; userId: string; name?: string; date?: string;
  }>();
  const groupId = Number(id);
  const patientId = Number(userId);
  const today = useKstToday();
  const initialDate = useMemo(() => resolveInitialDate(date), [date]);

  const [displayYear, setDisplayYear] = useState(() => Number(initialDate.slice(0, 4)));
  const [displayMonth, setDisplayMonth] = useState(() => Number(initialDate.slice(5, 7)));
  const [selectedDate, setSelectedDate] = useState(() => initialDate);
  const [pickerVisible, setPickerVisible] = useState(false);

  const { data: groupDetail } = useGetGroupDetailQuery(groupId, { skip: !Number.isFinite(groupId) });
  const {
    data: scheduleDay, error: dayError,
    isLoading: dayLoading, isFetching: dayFetching, refetch: refetchDay,
  } = useGetMemberDayScheduleQuery(
    { date: selectedDate, patientId, groupId },
    { skip: !Number.isFinite(patientId) || !Number.isFinite(groupId) },
  );
  const {
    data: monthAdherence, error: monthError,
    isLoading: monthLoading, isFetching: monthFetching, refetch: refetchMonth,
  } = useGetMemberMonthAdherenceQuery(
    { month: toMonthString(displayYear, displayMonth), patientId, groupId },
    { skip: !Number.isFinite(patientId) || !Number.isFinite(groupId) },
  );

  const memberName = useMemo(
    () => name ?? groupDetail?.members.find(m => m.userId === patientId)?.name ?? FALLBACK_MEMBER_NAME,
    [name, groupDetail, patientId],
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
    const [year, month] = date.split('-').map(Number);
    setDisplayYear(year);
    setDisplayMonth(month);
  }, []);

  if (isForbidden(dayError) || isForbidden(monthError)) {
    return <NoticeScreen memberName={memberName} groupId={groupId} message={FORBIDDEN_MSG} />;
  }

  // day·month 어느 쪽 오류든 감지 — 월간만 실패해도 달력이 조용히 회색(=미복용처럼)으로 뜨지 않게(의료 P0).
  if (dayError ?? monthError) {
    return (
      <NoticeScreen
        memberName={memberName}
        groupId={groupId}
        message={LOAD_FAILED_MSG}
        onRetry={() => { refetchDay(); refetchMonth(); }}
      />
    );
  }

  // fulfilled 이전(첫 로딩)엔 "복약 0/0"·"없어요" 확정 문구 대신 스켈레톤 — 보호자의 미복용 오해 방지(의료 P0).
  const dayReady = scheduleDay !== undefined;
  const monthReady = monthAdherence !== undefined;
  if ((!dayReady || !monthReady) && (dayLoading || dayFetching || monthLoading || monthFetching)) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <MemberHeader memberName={memberName} groupId={groupId} />
        <BootSkeleton />
      </SafeAreaView>
    );
  }

  const slots: MedSlot[] = scheduleDay?.slots ?? [];
  const doneCount = slots.filter(s => s.state === 'done').length;

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <MemberHeader memberName={memberName} groupId={groupId} />

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
          adherenceByDate={monthAdherence ?? {}}
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
        <View style={styles.dayArea}>
          <View style={styles.dayHeader}>
            <Text style={styles.dayLabel}>{formatDayLabel(selectedDate, today)}</Text>
            <Text style={styles.dayCount}>복약 {doneCount} / {slots.length} 완료</Text>
          </View>
          <View style={styles.medCard}>
            {slots.map((slot, i) => (
              <MedTimeRow key={`${slot.id}-${i}`} slot={slot} isFirst={i === 0} readOnly />
            ))}
            {slots.length === 0 && (
              <Text style={styles.emptyText}>이 날짜에 등록된 복약이 없어요</Text>
            )}
          </View>
          <Text style={styles.readOnlyNote}>{READ_ONLY_MSG}</Text>
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

function NoticeScreen({
  memberName, groupId, message, onRetry,
}: {
  memberName: string;
  groupId: number;
  message: string;
  onRetry?: () => void;
}) {
  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <MemberHeader memberName={memberName} groupId={groupId} />
      <View style={styles.noticeBox}>
        <Text style={styles.noticeText}>{message}</Text>
        {onRetry && (
          <Pressable
            style={styles.retryBtn}
            onPress={onRetry}
            accessibilityLabel={RETRY_LABEL}
            accessibilityRole="button"
          >
            <Text style={styles.retryTxt}>{RETRY_LABEL}</Text>
          </Pressable>
        )}
      </View>
    </SafeAreaView>
  );
}

function MemberHeader({ memberName, groupId }: { memberName: string; groupId: number }) {
  return (
    <View style={styles.header}>
      <Pressable
        onPress={() => safeBack(`/group/${groupId}`)}
        accessibilityLabel="뒤로가기"
        accessibilityRole="button"
        hitSlop={8}
      >
        <Feather name="chevron-left" size={scale(24)} color={colors.labelNormal} />
      </Pressable>
      <Text style={styles.headerTitle}>{memberName}님의 복약</Text>
      <View style={{ width: scale(24) }} />
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgNormal },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal, borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
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
  legend: {
    flexDirection: 'row', flexWrap: 'wrap', columnGap: space.s16, rowGap: space.s8,
    paddingHorizontal: space.s16, marginTop: space.s14, marginBottom: space.s12,
  },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  legendDot: { width: scale(6), height: scale(6), borderRadius: scale(3) },
  legendText: { fontSize: scale(12), color: colors.labelAlternative },
  separator: { height: scale(8), backgroundColor: colors.bgAlt },
  dayArea: { backgroundColor: colors.bgAlt, paddingBottom: space.s24 },
  dayHeader: { paddingHorizontal: space.s16, paddingTop: space.s20, paddingBottom: space.s12 },
  dayLabel: { fontSize: scale(12), color: colors.labelAlternative, fontWeight: '600', letterSpacing: 0.04 },
  dayCount: { ...typography.headline1, marginTop: 2, color: colors.labelNormal },
  medCard: {
    marginHorizontal: space.s16, backgroundColor: colors.bgNormal,
    borderRadius: radius.r16, borderWidth: 1, borderColor: colors.line, overflow: 'hidden',
  },
  emptyText: {
    fontSize: scale(13), color: colors.labelAlternative,
    textAlign: 'center', paddingVertical: space.s24,
  },
  readOnlyNote: {
    fontSize: scale(12), color: colors.labelAssistive,
    textAlign: 'center', marginTop: space.s12, paddingHorizontal: space.s16,
  },
  noticeBox: {
    margin: space.s16, padding: space.s20, borderRadius: radius.r12,
    backgroundColor: colors.bgAlt, alignItems: 'center', gap: space.s16,
  },
  noticeText: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center' },
  retryBtn: {
    paddingHorizontal: space.s20, paddingVertical: space.s10,
    borderRadius: radius.r12, backgroundColor: colors.primaryNormal,
  },
  retryTxt: { ...typography.label2, fontWeight: '700', color: '#fff' },
});
