import React, { useMemo, useState } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { createSelector } from 'reselect';
import { colors, typography, space, radius, shadows } from '@/styles/tokens';
import { useAppSelector, useAppDispatch } from '@/store/hooks';
import { clearUnread } from '@/store/slices/homeSlice';
import { useGetRecentActivityQuery } from '@/store/slices/activityApi';
import { useSlotPress } from '@/hooks/useSlotPress';
import type { RootState } from '@/store';
import GroupSelector from '@/components/home/GroupSelector';
import NotificationBell from '@/components/home/NotificationBell';
import TimeSlotCards, { TimeSlot } from '@/components/home/TimeSlotCards';
import InsightCard from '@/components/home/InsightCard';
import ActivityFeedItem from '@/components/home/ActivityFeedItem';
import type { ActivityFeedItem as ActivityFeedItemType } from '@/types/activity';
import { MFDS_SOURCE, ACTIVITY_POLL_INTERVAL_MS } from '@/lib/constants';

const selectHome = createSelector(
  (state: RootState) => state.home,
  (home) => home,
);

const MOCK_SLOTS: TimeSlot[] = [
  { id: 'morning', label: '아침',   time: '8:00',  state: 'done', drugCount: 2, pillColors: ['#A8D4FF', '#FFAA6B'], doseLogId: 4 },
  { id: 'noon',    label: '점심',   time: '12:30', state: 'now',  drugCount: 3, pillColors: ['#FFB3C1', '#F5F5F5'], doseLogId: 5 },
  { id: 'evening', label: '저녁',   time: '19:00', state: 'wait', drugCount: 2, pillColors: ['#C4B5FD'],            doseLogId: 6 },
  { id: 'bedtime', label: '취침 전', time: '22:00', state: 'wait', drugCount: 1, pillColors: ['#0066FF'],            doseLogId: 7 },
];

export default function HomeScreen() {
  const dispatch = useAppDispatch();
  const { unreadCount } = useAppSelector(selectHome);
  const doseStateMap = useAppSelector((state: RootState) => state.doseState);
  const [showInsight, setShowInsight] = useState(true);
  const handleBellPress = useMemo(() => () => dispatch(clearUnread()), [dispatch]);
  const pressSlot = useSlotPress();

  const { data: feed = [], isLoading: feedLoading, isError: feedError } =
    useGetRecentActivityQuery(undefined, { pollingInterval: ACTIVITY_POLL_INTERVAL_MS });

  const slots = useMemo(
    () => MOCK_SLOTS.map(s => {
      const entry = s.doseLogId != null ? doseStateMap[s.doseLogId] : undefined;
      return { ...s, state: (entry?.state ?? s.state) as typeof s.state };
    }),
    [doseStateMap],
  );

  const handleSlotPress = useMemo(
    () => (slot: TimeSlot) => pressSlot(slot.doseLogId, slot.state),
    [pressSlot],
  );

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* ── Header ── */}
      <View style={styles.header}>
        <View style={styles.headerTopRow}>
          <GroupSelector
            groupName="할머니 댁 · 3명"
            onPress={() => { /* Phase 2: group switcher */ }}
          />
          <NotificationBell count={unreadCount} onPress={handleBellPress} />
        </View>

        <Text style={styles.greeting}>안녕하세요, 민지님</Text>
        <Text style={styles.subtitle}>오늘 할머니 복약 4/6 완료</Text>

        <View style={styles.progressTrack}>
          <View style={styles.progressFill} />
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
            <Text style={styles.sectionDate}>11월 24일 월</Text>
          </View>
          <TimeSlotCards slots={slots} onSlotPress={handleSlotPress} />
        </View>

        {/* AI 인사이트 */}
        {showInsight && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>AI 인사이트</Text>
            <InsightCard
              severity="WARN"
              message="저녁약을 3일째 빠뜨렸어요"
              detail="메트포르민을 거르면 혈당 조절이 어려워질 수 있어요. 저녁 식사 후 바로 복용하는 습관을 들여보세요."
              onClose={() => setShowInsight(false)}
              onDetail={() => router.push('/report' as any)}
            />
          </View>
        )}

        {/* 가족 활동 */}
        <View style={styles.section}>
          <View style={styles.sectionRow}>
            <Text style={styles.sectionTitle}>가족 활동</Text>
            <Pressable
              accessibilityLabel="전체보기"
              accessibilityRole="button"
              onPress={() => { /* Phase 2: /activity */ }}
            >
              <Text style={styles.viewAll}>전체보기</Text>
            </Pressable>
          </View>

          <FamilyActivityFeed
            feed={feed}
            isLoading={feedLoading}
            isError={feedError}
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

interface FamilyActivityFeedProps {
  feed: ActivityFeedItemType[];
  isLoading: boolean;
  isError: boolean;
}

function FamilyActivityFeed({ feed = [], isLoading, isError }: FamilyActivityFeedProps) {
  if (isLoading) {
    return (
      <View style={styles.feedPlaceholder} testID="activity-loading">
        <ActivityIndicator size="small" color={colors.primaryBase} />
        <Text style={styles.feedPlaceholderTxt}>활동 불러오는 중…</Text>
      </View>
    );
  }
  if (isError) {
    return (
      <View style={styles.feedPlaceholder} testID="activity-error">
        <Text style={styles.feedPlaceholderTxt}>활동을 불러올 수 없어요</Text>
      </View>
    );
  }
  if (feed.length === 0) {
    return (
      <View style={styles.feedPlaceholder} testID="activity-empty">
        <Text style={styles.feedPlaceholderTxt}>아직 가족 활동이 없어요</Text>
      </View>
    );
  }
  return (
    <View style={styles.feedCard} testID="activity-data">
      {feed.map((item, idx) => (
        <React.Fragment key={`${item.actorNickname}-${item.occurredAt}`}>
          <ActivityFeedItem item={item} />
          {idx < feed.length - 1 && <View style={styles.separator} />}
        </React.Fragment>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    backgroundColor: colors.bgNormal,
    paddingHorizontal: space.s20,
    paddingTop: space.s12,
    paddingBottom: space.s20,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
  },
  headerTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: space.s12,
  },
  greeting: { fontSize: 26, fontWeight: '700', color: colors.labelNormal, letterSpacing: -0.6 },
  subtitle: { ...typography.label1n, color: colors.labelAlternative, marginTop: 2 },
  progressTrack: {
    height: 8, borderRadius: radius.full, backgroundColor: colors.fillStrong,
    marginTop: space.s16, overflow: 'hidden',
  },
  progressFill: { width: '67%', height: '100%', borderRadius: radius.full, backgroundColor: colors.primaryBase },
  scroll: { flex: 1 },
  scrollContent: { padding: space.s16, gap: space.s20, paddingBottom: space.s40 },
  section: { gap: space.s10 },
  sectionRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sectionTitle: { fontSize: 18, fontWeight: '700', color: colors.labelNormal },
  sectionDate: { fontSize: 13, fontWeight: '600', color: colors.labelAlternative },
  viewAll: { fontSize: 13, fontWeight: '600', color: colors.primaryNormal },
  feedCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, overflow: 'hidden', ...shadows.small,
  },
  feedPlaceholder: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, padding: space.s20,
    alignItems: 'center', gap: space.s8,
  },
  feedPlaceholderTxt: { ...typography.body2r, color: colors.labelAlternative },
  separator: { height: 1, backgroundColor: colors.line, marginLeft: 64 },
  safetyFooter: {
    padding: space.s12, borderRadius: radius.r12, backgroundColor: '#F0F7FF',
    borderWidth: 1, borderColor: '#C8DDFF',
  },
  safetyText: { ...typography.caption1, color: colors.labelAlternative, textAlign: 'center', lineHeight: 18 },
});
