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
import BootSkeleton from '@/components/common/BootSkeleton';

// 콜드 스타트 SWR 재검증은 유지하되, 웜 네비게이션(탭 재진입)마다 3중 재fetch 되는 부수효과는 완화 (CTO 결정).
const HOME_REFETCH_THROTTLE_SEC = 30;

export default function HomeScreen() {
  const today = getKstToday();
  const doseStateMap = useAppSelector((state: RootState) => state.doseState);
  const handleSettingsPress = useMemo(() => () => router.push('/(tabs)/my' as any), []);
  const pressSlot = useSlotPress();

  const { pinnedGroupId } = useGetMyGroupsQuery(undefined, {
    selectFromResult: ({ data }) => ({ pinnedGroupId: data?.find(g => g.pinned)?.groupId }),
    refetchOnMountOrArgChange: HOME_REFETCH_THROTTLE_SEC,
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

  const { data: insightList = [] } = useGetActiveWithInsightsQuery(undefined, {
    refetchOnMountOrArgChange: HOME_REFETCH_THROTTLE_SEC,
  });
  const rotateIndex = useRotatingIndex(insightList.length, INSIGHT_ROTATE_INTERVAL_MS);
  const currentInsight = insightList[rotateIndex] ?? null;
  const insight = currentInsight?.insights?.[0] ?? null;

  const {
    data: scheduleDay, isLoading: scheduleLoading, isError: scheduleError, refetch: refetchSchedule,
  } = useGetDayScheduleQuery(today, { refetchOnMountOrArgChange: HOME_REFETCH_THROTTLE_SEC });
  // fulfilled 이전(로딩·에러)에는 "없어요" 확정 문구를 절대 띄우지 않는다 — 오프라인/첫실행에 오늘 복약 스킵 유도 위험(의료 P0).
  const scheduleReady = scheduleDay !== undefined;
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

  // 캐시(rehydrate 포함) 없이 로딩/에러 상태면 "없어요" 확정 문구 대신 스켈레톤/재시도로 분기.
  if (!scheduleReady && scheduleLoading) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <BootSkeleton />
      </SafeAreaView>
    );
  }
  if (!scheduleReady && scheduleError) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <View style={styles.errorState}>
          <Text style={styles.errorText}>일정을 불러오지 못했어요</Text>
          <Pressable
            style={styles.retryBtn}
            onPress={() => refetchSchedule()}
            accessibilityLabel="재시도"
            accessibilityRole="button"
          >
            <Text style={styles.retryTxt}>재시도</Text>
          </Pressable>
        </View>
      </SafeAreaView>
    );
  }

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
          {scheduleReady && slots.length === 0 ? (
            <Pressable
              style={styles.emptyCard}
              onPress={() => router.push('/prescription' as any)}
              accessibilityRole="button"
              accessibilityLabel="약봉투 등록하러 가기"
              accessibilityHint="약봉투 등록 화면으로 이동합니다"
            >
              <View style={styles.emptyIcon}>
                <Feather name="file-plus" size={scale(26)} color={colors.primaryNormal} />
              </View>
              <View style={styles.emptyTextArea}>
                <Text style={styles.emptyTitle}>아직 등록한 약봉투가 없어요</Text>
                <Text style={styles.emptySub}>약봉투 메뉴에서 약을 등록해 주세요</Text>
              </View>
              <Feather name="chevron-right" size={scale(22)} color={colors.labelAssistive} />
            </Pressable>
          ) : (
            <TimeSlotCards
              slots={slots}
              onSlotPress={handleSlotPress}
            />
          )}
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
  emptyCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.s12,
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16,
    borderWidth: 1,
    borderColor: colors.line,
    padding: space.s16,
  },
  emptyIcon: {
    width: scale(44),
    height: scale(44),
    borderRadius: radius.full,
    backgroundColor: colors.bgAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyTextArea: { flex: 1, gap: scale(2) },
  emptyTitle: { ...typography.headline1, fontWeight: '700', color: colors.labelNormal },
  emptySub: { ...typography.body2r, color: colors.labelAlternative },
  errorState: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space.s16, padding: space.s24 },
  errorText: { ...typography.body1n, color: colors.labelAlternative },
  retryBtn: {
    paddingHorizontal: space.s20, paddingVertical: space.s10,
    borderRadius: radius.r12, backgroundColor: colors.primaryNormal,
  },
  retryTxt: { ...typography.label2, fontWeight: '700', color: '#fff' },
});
