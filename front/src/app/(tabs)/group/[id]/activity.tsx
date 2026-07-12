import React, { useCallback, useMemo, useState } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable, ActivityIndicator, Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { scale, colors, space, typography } from '@/styles/tokens';
import { useGetRecentActivityQuery } from '@/store/slices/activityApi';
import { useGetGroupDetailQuery } from '@/store/slices/caregroupApi';
import DaySection from '@/components/group/DaySection';
import { safeBack } from '@/lib/router/safeBack';
import { filterByDateRange, type DateRangeFilter } from '@/lib/activityDateFilter';
import type { ActivityView } from '@/types/caregroup';
import type { ActivityFeedItem } from '@/types/activity';

const TABS: { id: DateRangeFilter; label: string }[] = [
  { id: 'today', label: '오늘' },
  { id: 'week',  label: '7일' },
  { id: 'month', label: '30일' },
  { id: 'custom', label: '기간' },
];

export default function ActivityScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const groupId = Number(id);
  const [activeRange, setActiveRange] = useState<DateRangeFilter>('week');

  const { data: feed = [], isLoading } = useGetRecentActivityQuery({ groupId });
  const { data: detail } = useGetGroupDetailQuery(groupId);

  const handleTabPress = useCallback((range: DateRangeFilter) => {
    if (range === 'custom') {
      Alert.alert('기간 선택', '사용자 정의 기간 선택은 추후 지원 예정이에요.');
      return;
    }
    setActiveRange(range);
  }, []);

  const filtered = useMemo(
    () => filterByDateRange(feed.map(toActivityView), activeRange),
    [feed, activeRange],
  );
  const grouped = useMemo(() => groupByDay(filtered), [filtered]);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Pressable onPress={() => safeBack(`/group/${groupId}`)} accessibilityLabel="뒤로가기" accessibilityRole="button" hitSlop={8}>
          <Feather name="chevron-left" size={scale(26)} color={colors.labelNormal} />
        </Pressable>
        <View style={styles.headerTitleCol}>
          <Text style={styles.headerTitle}>가족 활동</Text>
          {detail && <Text style={styles.headerSub}>{detail.name} · {detail.memberCount}명</Text>}
        </View>
        <Pressable accessibilityLabel="필터" accessibilityRole="button" hitSlop={8}>
          <Feather name="filter" size={scale(20)} color={colors.labelNormal} />
        </Pressable>
      </View>

      <View style={styles.tabsRow}>
        {TABS.map(t => {
          const on = t.id === activeRange;
          return (
            <Pressable
              key={t.id}
              style={[styles.tab, on && styles.tabActive]}
              onPress={() => handleTabPress(t.id)}
              accessibilityRole="button"
            >
              <Text style={[styles.tabText, on && styles.tabTextActive]}>{t.label}</Text>
            </Pressable>
          );
        })}
      </View>

      <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        {isLoading && <ActivityIndicator color={colors.primaryBase} style={styles.loader} />}
        {grouped.today.length > 0 && <DaySection title={`오늘 · ${formatHeader(new Date())}`} items={grouped.today} first />}
        {grouped.yesterday.length > 0 && <DaySection title={`어제 · ${formatHeader(addDays(new Date(), -1))}`} items={grouped.yesterday} />}
        {grouped.earlier.length > 0 && <DaySection title="이전 활동" items={grouped.earlier} />}
        {!isLoading && filtered.length === 0 && (
          <Text style={styles.empty}>표시할 활동이 없어요</Text>
        )}
        <Text style={styles.footer}>최근 30일 활동만 표시됩니다</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

function toActivityView(f: ActivityFeedItem): ActivityView {
  return {
    actorName: f.actorNickname,
    activityType: f.activityType,
    summary: f.summary,
    occurredAt: f.occurredAt,
  };
}

interface GroupedActivities {
  today: ActivityView[];
  yesterday: ActivityView[];
  earlier: ActivityView[];
}

function groupByDay(items: ActivityView[]): GroupedActivities {
  const now = new Date();
  const today = now.toDateString();
  const yesterday = addDays(now, -1).toDateString();
  return items.reduce<GroupedActivities>((acc, it) => {
    const d = new Date(it.occurredAt).toDateString();
    if (d === today) acc.today.push(it);
    else if (d === yesterday) acc.yesterday.push(it);
    else acc.earlier.push(it);
    return acc;
  }, { today: [], yesterday: [], earlier: [] });
}

function addDays(d: Date, n: number): Date {
  const r = new Date(d);
  r.setDate(r.getDate() + n);
  return r;
}

function formatHeader(d: Date): string {
  const months = ['1월','2월','3월','4월','5월','6월','7월','8월','9월','10월','11월','12월'];
  const days = ['일','월','화','수','목','금','토'];
  return `${months[d.getMonth()]} ${d.getDate()}일 ${days[d.getDay()]}`;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitleCol: { flex: 1 },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  headerSub: { fontSize: scale(12), color: colors.labelAlternative, marginTop: 2 },
  tabsRow: {
    flexDirection: 'row', gap: space.s6,
    paddingHorizontal: space.s20, paddingTop: space.s4, paddingBottom: space.s14,
    backgroundColor: colors.bgNormal,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  tab: {
    paddingHorizontal: space.s14, paddingVertical: space.s8,
    borderRadius: scale(9999), backgroundColor: colors.fillNormal,
  },
  tabActive: { backgroundColor: colors.labelNormal },
  tabText: { fontSize: scale(13), fontWeight: '500', color: colors.labelAlternative },
  tabTextActive: { color: colors.staticWhite, fontWeight: '700' },
  scroll: { flex: 1 },
  scrollContent: { paddingBottom: 24 },
  loader: { marginTop: space.s40 },
  empty: { textAlign: 'center', color: colors.labelAlternative, padding: space.s40 },
  footer: {
    padding: space.s32, paddingHorizontal: space.s20,
    textAlign: 'center', fontSize: scale(12), color: colors.labelAssistive,
  },
});
