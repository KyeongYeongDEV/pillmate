import React, { useCallback, useMemo, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  ScrollView,
  StyleSheet,
  Pressable,
  ActivityIndicator,
  ListRenderItem,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { createSelector } from 'reselect';
import { colors, typography, space, radius, shadows } from '@/styles/tokens';
import { useAppSelector, useAppDispatch } from '@/store/hooks';
import { clearUnread } from '@/store/slices/homeSlice';
import {
  useGetRecentActivityQuery,
  useGetTodayDoseProgressQuery,
  useGetInsightsQuery,
  ActivityItem,
} from '@/store/slices/activityApi';
import type { RootState } from '@/store';
import GroupSelector from '@/components/home/GroupSelector';
import NotificationBell from '@/components/home/NotificationBell';
import TodayProgressCard from '@/components/home/TodayProgressCard';
import TimeSlotCards, { TimeSlot } from '@/components/home/TimeSlotCards';
import InsightCard from '@/components/home/InsightCard';
import ActivityFeedItem from '@/components/home/ActivityFeedItem';
import { ACTIVITY_POLL_INTERVAL_MS, MFDS_SOURCE } from '@/lib/constants';

// Selectors
const selectHome = createSelector(
  (state: RootState) => state.home,
  (home) => home,
);

// Phase 1 mock data — BE activityApi not yet implemented
const MOCK_ACTIVITY: ActivityItem[] = [
  {
    id: 1,
    actorName: '할머니',
    actorEmoji: '👴',
    action: '아침약 2개 복용 완료',
    detail: '',
    occurredAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
  },
  {
    id: 2,
    actorName: '엄마',
    actorEmoji: '👩',
    action: '새 처방전 등록',
    detail: '감기약',
    occurredAt: new Date(Date.now() - 12 * 60 * 1000).toISOString(),
  },
  {
    id: 3,
    actorName: 'AI',
    actorEmoji: '✨',
    action: '주간 리포트가 도착했어요',
    detail: '',
    occurredAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
  },
];

const MOCK_SLOTS: TimeSlot[] = [
  { id: 'morning', label: '아침', time: '08:00', drugCount: 2, status: 'done' },
  { id: 'noon', label: '점심', time: '12:00', drugCount: 3, status: 'current' },
  { id: 'evening', label: '저녁', time: '19:00', drugCount: 2, status: 'pending' },
  { id: 'bedtime', label: '취침', time: '22:00', drugCount: 1, status: 'pending' },
];

const MOCK_PROGRESS = { taken: 4, total: 6, nextScheduleTime: '12:00', nextScheduleLabel: '점심약' };

export default function HomeScreen() {
  const dispatch = useAppDispatch();
  const { unreadCount, activeGroupId } = useAppSelector(selectHome);
  const [showInsight, setShowInsight] = useState(true);

  // Phase 1: API not yet implemented — fall back to mock
  const { data: progress } = useGetTodayDoseProgressQuery(
    { patientId: 1 },
    { skip: false },
  );
  const { data: insight } = useGetInsightsQuery({ patientId: 1 });
  const { data: activityData } = useGetRecentActivityQuery(
    { groupId: activeGroupId ?? 1, limit: 5 },
    { pollingInterval: ACTIVITY_POLL_INTERVAL_MS },
  );

  const todayProgress = progress ?? MOCK_PROGRESS;
  const activityFeed = activityData ?? MOCK_ACTIVITY;
  const insightData = showInsight ? (insight ?? {
    severity: 'WARN' as const,
    message: '저녁약을 자주 빠뜨려요',
    detail: '지난 30일 중 7일(23%) 메트포르민 누락',
  }) : null;

  const handleSlotPress = useCallback((slot: TimeSlot) => {
    // Phase 2: open BottomSheet with slot detail
  }, []);

  const handleBellPress = useCallback(() => {
    dispatch(clearUnread());
    // Phase 2: navigate to notification list
  }, [dispatch]);

  const handleActivityPress = useCallback((_item: ActivityItem) => {
    // Phase 2: navigate to activity detail
  }, []);

  const renderActivity: ListRenderItem<ActivityItem> = useCallback(
    ({ item }) => <ActivityFeedItem item={item} onPress={handleActivityPress} />,
    [handleActivityPress],
  );

  const keyExtractor = useCallback((item: ActivityItem) => String(item.id), []);

  const getItemLayout = useCallback(
    (_: unknown, index: number) => ({ length: 60, offset: 60 * index, index }),
    [],
  );

  return (
    <SafeAreaView style={styles.root} edges={['top']}>
      {/* Sticky Header */}
      <View style={styles.header}>
        <GroupSelector
          groupName="우리 가족 (할머니 댁)"
          onPress={() => {/* Phase 2: BottomSheet group list */}}
        />
        <NotificationBell count={unreadCount} onPress={handleBellPress} />
      </View>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Progress Card */}
        <TodayProgressCard progress={todayProgress} />

        {/* Time Slot Cards */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>오늘의 복약</Text>
          <TimeSlotCards slots={MOCK_SLOTS} onSlotPress={handleSlotPress} />
        </View>

        {/* AI Insight Card */}
        {insightData && (
          <InsightCard
            severity={insightData.severity}
            message={insightData.message}
            detail={insightData.detail}
            onClose={() => setShowInsight(false)}
            onDetail={() => router.push('/report' as any)}
          />
        )}

        {/* Activity Feed */}
        <View style={styles.feedSection}>
          <View style={styles.feedHeader}>
            <Text style={styles.sectionTitle}>가족 활동</Text>
            <Pressable
              accessibilityLabel="전체 보기"
              accessibilityRole="button"
              onPress={() => {/* Phase 2: /activity */}}
            >
              <Text style={styles.feedMore}>전체 보기 →</Text>
            </Pressable>
          </View>
          <View style={styles.feedCard}>
            <FlatList
              data={activityFeed}
              renderItem={renderActivity}
              keyExtractor={keyExtractor}
              getItemLayout={getItemLayout}
              scrollEnabled={false}
              ItemSeparatorComponent={() => <View style={styles.separator} />}
            />
          </View>
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
  root: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.s16,
    paddingVertical: space.s12,
    backgroundColor: colors.bgNormal,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
  },
  scroll: { flex: 1 },
  scrollContent: {
    padding: space.s16,
    gap: space.s16,
    paddingBottom: space.s40,
  },
  section: {
    gap: space.s10,
  },
  sectionTitle: {
    ...typography.label2,
    color: colors.labelAlternative,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  feedSection: {
    gap: space.s10,
  },
  feedHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  feedMore: {
    ...typography.label2,
    color: colors.primaryNormal,
    fontWeight: '600',
  },
  feedCard: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16,
    borderWidth: 1,
    borderColor: colors.line,
    overflow: 'hidden',
    ...shadows.small,
  },
  separator: {
    height: 1,
    backgroundColor: colors.line,
    marginLeft: 64,
  },
  safetyFooter: {
    padding: space.s12,
    borderRadius: radius.r12,
    backgroundColor: '#F0F7FF',
    borderWidth: 1,
    borderColor: '#C8DDFF',
  },
  safetyText: {
    ...typography.caption1,
    color: colors.labelAlternative,
    textAlign: 'center',
    lineHeight: 18,
  },
});
