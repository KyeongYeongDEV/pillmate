import React, { useState, useCallback, useMemo } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import { router } from 'expo-router';
import * as Haptics from 'expo-haptics';
import { colors, space, radius, typography, shadows } from '@/styles/tokens';
import { useGetMyGroupsQuery, usePinGroupMutation, useUnpinGroupMutation } from '@/store/slices/caregroupApi';
import FilterChips, { GroupFilter } from '@/components/group/FilterChips';
import GroupCard from '@/components/group/GroupCard';
import type { MyGroupSummary } from '@/types/caregroup';

export default function GroupScreen() {
  const [filter, setFilter] = useState<GroupFilter>('전체');
  const { data: groups = [], isLoading, isError } = useGetMyGroupsQuery();
  const [pinGroup] = usePinGroupMutation();
  const [unpinGroup] = useUnpinGroupMutation();

  const filteredGroups = useMemo(() => applyFilter(groups, filter), [groups, filter]);
  const pinnedGroup = useMemo(() => groups.find(g => g.pinned) ?? null, [groups]);
  const unpinnedGroups = useMemo(
    () => filteredGroups.filter(g => !g.pinned),
    [filteredGroups],
  );

  const handleCardPress = useCallback((groupId: number) => {
    router.push(`/group/${groupId}` as any);
  }, []);

  const handlePinToggle = useCallback(async (groupId: number, currentlyPinned: boolean) => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    if (currentlyPinned) {
      unpinGroup(groupId);
    } else {
      pinGroup(groupId);
    }
  }, [pinGroup, unpinGroup]);

  const handleCreate = useCallback(() => {
    router.push('/group/create' as any);
  }, []);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>그룹</Text>
        <View style={styles.headerRight}>
          <Text style={styles.headerCount}>{groups.length}개</Text>
          <Pressable accessibilityLabel="그룹 검색" accessibilityRole="button" hitSlop={8}>
            <Feather name="search" size={20} color={colors.labelNormal} />
          </Pressable>
        </View>
      </View>

      <FilterChips selected={filter} onSelect={setFilter} />

      <ScrollView style={styles.scroll} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {isLoading && <ActivityIndicator size="large" color={colors.primaryBase} style={styles.loader} />}
        {isError && <ErrorPlaceholder />}

        {/* 📌 고정됨 */}
        {pinnedGroup && (
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>📌 고정됨</Text>
            <GroupCard
              group={pinnedGroup}
              onPress={handleCardPress}
              onPinToggle={handlePinToggle}
              isPinned
            />
          </View>
        )}

        {/* 모든 그룹 */}
        <View style={styles.section}>
          <Text style={styles.sectionLabel}>모든 그룹 · {filteredGroups.length}</Text>
          {unpinnedGroups.map((g) => (
            <GroupCard
              key={g.groupId}
              group={g}
              onPress={handleCardPress}
              onPinToggle={handlePinToggle}
            />
          ))}
          {unpinnedGroups.length === 0 && !isLoading && (
            <Text style={styles.emptyText}>표시할 그룹이 없어요</Text>
          )}
        </View>

        {/* 새 그룹 CTA */}
        <Pressable
          style={styles.ctaCard}
          onPress={handleCreate}
          accessibilityLabel="새 그룹 만들기"
          accessibilityRole="button"
        >
          <Feather name="plus-circle" size={22} color={colors.primaryBase} />
          <Text style={styles.ctaText}>새 그룹 만들기</Text>
          <Feather name="chevron-right" size={18} color={colors.labelAlternative} />
        </Pressable>
      </ScrollView>

      {/* FAB */}
      <Pressable
        style={styles.fab}
        onPress={handleCreate}
        accessibilityLabel="그룹 만들기"
        accessibilityRole="button"
      >
        <Feather name="plus" size={24} color="#fff" />
      </Pressable>
    </SafeAreaView>
  );
}

function ErrorPlaceholder() {
  return (
    <View style={styles.errorBox}>
      <Text style={styles.errorText}>그룹 목록을 불러올 수 없어요</Text>
    </View>
  );
}

function applyFilter(groups: MyGroupSummary[], filter: GroupFilter): MyGroupSummary[] {
  if (filter === '전체') return groups;
  if (filter === '내가 환자') return groups.filter(g => g.role === '환자');
  if (filter === '내가 보호자') return groups.filter(g => g.role === '보호자');
  if (filter === '비공개') return [];
  return groups;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal, borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: space.s12 },
  headerCount: { fontSize: 13, color: colors.labelAlternative, fontWeight: '600' },
  scroll: { flex: 1 },
  content: { paddingVertical: space.s16, gap: space.s8, paddingBottom: 100 },
  loader: { marginTop: space.s40 },
  section: { paddingHorizontal: space.s16, gap: space.s10 },
  sectionLabel: {
    fontSize: 11, fontWeight: '700', color: colors.labelAlternative, letterSpacing: 0.06,
  },
  emptyText: { fontSize: 14, color: colors.labelAlternative, textAlign: 'center', paddingVertical: space.s20 },
  errorBox: { margin: space.s16, padding: space.s16, borderRadius: radius.r12, backgroundColor: colors.bgNormal },
  errorText: { fontSize: 14, color: colors.labelAlternative, textAlign: 'center' },
  ctaCard: {
    marginHorizontal: space.s16,
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    padding: space.s16, borderWidth: 1, borderColor: colors.line,
    ...shadows.small,
  },
  ctaText: { flex: 1, fontSize: 15, fontWeight: '600', color: colors.primaryBase },
  fab: {
    position: 'absolute', bottom: 24, right: 20,
    width: 56, height: 56, borderRadius: 28,
    backgroundColor: colors.primaryBase,
    alignItems: 'center', justifyContent: 'center',
    ...shadows.medium,
  },
});
