import React, { useMemo } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { skipToken } from '@reduxjs/toolkit/query';
import { colors, typography, space, radius, shadows, scale } from '@/styles/tokens';
import { useAppSelector } from '@/store/hooks';
import { useGetRecentActivityQuery } from '@/store/slices/activityApi';
import { useGetMyGroupsQuery } from '@/store/slices/caregroupApi';
import { useGetActiveWithInsightsQuery } from '@/store/slices/prescriptionApi';
import { buildInsightSubtitle } from '@/lib/prescriptionInsight';
import { useSlotPress } from '@/hooks/useSlotPress';
import { useRotatingIndex } from '@/hooks/useRotatingIndex';
import type { RootState } from '@/store';
import TimeSlotCards, { TimeSlot } from '@/components/home/TimeSlotCards';
import InsightCard from '@/components/home/InsightCard';
import FamilyActivityFeed from '@/components/home/FamilyActivityFeed';
import { MFDS_SOURCE, ACTIVITY_POLL_INTERVAL_MS, INSIGHT_ROTATE_INTERVAL_MS } from '@/lib/constants';
import { useGetDayScheduleQuery } from '@/store/slices/scheduleApi';
import {
  medSlotToTimeSlot, buildDoseHeadline, deriveSlotStatuses, STREAK_DISPLAY_MIN,
  deriveOverlayState,
} from '@/lib/scheduleUtils';
import { formatFullDate, formatMonthDay, getKstToday } from '@/utils/calendarUtils';
import { useDoseStreak } from '@/hooks/useDoseStreak';
import DoseStatusRow from '@/components/home/DoseStatusRow';
import NotificationBell from '@/components/home/NotificationBell';

export default function HomeScreen() {
  const today = getKstToday();
  const doseStateMap = useAppSelector((state: RootState) => state.doseState);
  const handleSettingsPress = useMemo(() => () => router.push('/(tabs)/my' as any), []);
  const pressSlot = useSlotPress();

  const { pinnedGroupId } = useGetMyGroupsQuery(undefined, {
    selectFromResult: ({ data }) => ({ pinnedGroupId: data?.find(g => g.pinned)?.groupId }),
  });

  const { data: feed = [], isLoading: feedLoading, isError: feedError } =
    useGetRecentActivityQuery(
      pinnedGroupId != null ? { groupId: pinnedGroupId, limit: 10 } : skipToken,
      {
        pollingInterval: ACTIVITY_POLL_INTERVAL_MS,
        refetchOnFocus: true,
        refetchOnMountOrArgChange: true,
      },
    );

  const { data: insightList = [] } = useGetActiveWithInsightsQuery();
  const rotateIndex = useRotatingIndex(insightList.length, INSIGHT_ROTATE_INTERVAL_MS);
  const currentInsight = insightList[rotateIndex] ?? null;
  const insight = currentInsight?.insights?.[0] ?? null;

  const { data: scheduleDay } = useGetDayScheduleQuery(today);
  const rawSlots = scheduleDay?.slots ?? [];

  const slots = useMemo(
    () => rawSlots.map(s => {
      const ids = s.doseLogIds?.length ? s.doseLogIds : s.doseLogId != null ? [s.doseLogId] : [];
      const state = deriveOverlayState(ids, s.state, doseStateMap);
      return medSlotToTimeSlot({ ...s, state });
    }),
    [rawSlots, doseStateMap],
  );

  const handleSlotPress = useMemo(
    () => (slot: TimeSlot) => pressSlot(slot.doseLogIds, slot.state),
    [pressSlot],
  );

  const now = new Date();
  const doneCount = slots.filter(s => s.state === 'done').length;
  const todayComplete = slots.length > 0 && doneCount === slots.length;
  const streak = useDoseStreak(today, todayComplete);
  const slotStatuses = deriveSlotStatuses(slots, now);
  const dots = slots.map((s, i) => ({ label: s.label, status: slotStatuses[i] }));
  const showStreak = streak >= STREAK_DISPLAY_MIN;

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* ── Header ── */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <Text style={styles.dateLabel}>{formatFullDate(today)}</Text>
          <Text style={styles.greeting}>{buildDoseHeadline(slots, now)}</Text>
          <DoseStatusRow
            dots={dots}
            doneCount={doneCount}
            totalCount={slots.length}
            streak={streak}
            showStreak={showStreak}
          />
        </View>
        <View style={styles.headerRight}>
          <NotificationBell />
          <Pressable
            onPress={handleSettingsPress}
            accessibilityLabel="설정"
            accessibilityRole="button"
          >
            <Feather name="settings" size={scale(22)} color={colors.labelNormal} />
          </Pressable>
        </View>
      </View>

      {/* ── Body ── */}
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* 오늘의 복약 */}
        <View style={styles.section}>
          <View style={styles.sectionRow}>
            <Text style={styles.sectionTitle}>오늘의 복약</Text>
            <Text style={styles.sectionDate}>{formatMonthDay(today)}</Text>
          </View>
          <TimeSlotCards
            slots={slots}
            onSlotPress={handleSlotPress}
          />
        </View>

        {/* AI 인사이트 — 복약중 처방전 인사이트를 10초마다 순환. 없으면 숨김 */}
        {insight && currentInsight && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>AI 인사이트</Text>
            <InsightCard
              severity={insight.severity}
              message={insight.title}
              detail={insight.description}
              subtitle={buildInsightSubtitle(currentInsight.prescribedAt, currentInsight.drugCount)}
              onDetail={() => router.push({
                pathname: '/prescription/[id]',
                params: { id: String(currentInsight.prescriptionId) },
              } as any)}
            />
          </View>
        )}

        {/* 고정 그룹 알림 */}
        <View style={styles.section}>
          <View style={styles.sectionRow}>
            <Text style={styles.sectionTitle}>고정 그룹 알림</Text>
            {pinnedGroupId != null && (
              <Pressable
                onPress={() => router.push({ pathname: '/group/[id]', params: { id: String(pinnedGroupId) } } as any)}
                accessibilityLabel="그룹으로 이동"
                accessibilityRole="button"
                hitSlop={8}
              >
                <Text style={styles.sectionLink}>그룹으로 이동 ›</Text>
              </Pressable>
            )}
          </View>

          <FamilyActivityFeed
            feed={feed}
            isLoading={feedLoading}
            isError={feedError}
            hasPinnedGroup={pinnedGroupId != null}
          />
        </View>

        {/* Medical safety footer — always visible */}
        <View style={styles.safetyFooter}>
          <Text style={styles.safetyText}>
            약 정보 출처: {MFDS_SOURCE} · 정확한 복약 정보는 약사·의사와 상담하세요
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: colors.bgNormal,
    paddingHorizontal: space.s20,
    paddingTop: space.s12,
    paddingBottom: space.s10,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
  },
  headerLeft: { flex: 1 },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: space.s16, paddingTop: space.s2 },
  dateLabel: { ...typography.label1n, color: colors.labelAlternative },
  greeting: { ...typography.heading2, lineHeight: scale(25), color: colors.labelNormal, letterSpacing: -0.6, marginTop: scale(1) },
  scroll: { flex: 1 },
  scrollContent: { padding: space.s16, gap: space.s20, paddingBottom: space.s40 },
  section: { gap: space.s10 },
  sectionRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sectionTitle: { ...typography.headline1, fontWeight: '700', color: colors.labelNormal },
  sectionDate: { ...typography.label2, fontWeight: '600', color: colors.labelAlternative },
  viewAll: { ...typography.label2, fontWeight: '600', color: colors.primaryNormal },
  sectionLink: { ...typography.label2, fontWeight: '600', color: colors.primaryNormal },
  safetyFooter: {
    padding: space.s12, borderRadius: radius.r12, backgroundColor: '#F0F7FF',
    borderWidth: 1, borderColor: '#C8DDFF',
  },
  safetyText: { ...typography.caption1, color: colors.labelAlternative, textAlign: 'center', lineHeight: scale(18) },
});
