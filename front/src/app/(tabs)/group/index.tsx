import React, { useCallback, useMemo } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import { router } from 'expo-router';
import * as Haptics from 'expo-haptics';
import { scale, colors, space, radius, shadows } from '@/styles/tokens';
import TabHeader from '@/components/navigation/TabHeader';
import { useGetMyGroupsQuery, usePinGroupMutation, useUnpinGroupMutation } from '@/store/slices/caregroupApi';
import GroupCard from '@/components/group/GroupCard';
import { GROUP_LIST_REFRESH } from '@/lib/query/refreshOptions';

export default function GroupScreen() {
  const { data: groups = [], isLoading, isError } = useGetMyGroupsQuery(undefined, GROUP_LIST_REFRESH);
  const [pinGroup] = usePinGroupMutation();
  const [unpinGroup] = useUnpinGroupMutation();

  // BE PinGroupUseCase 단일 핀 — 새 핀 시 기존 해제
  const pinnedGroup = useMemo(() => groups.find(g => g.pinned) ?? null, [groups]);
  const unpinnedGroups = useMemo(
    () => groups.filter(g => g.groupId !== pinnedGroup?.groupId),
    [groups, pinnedGroup],
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

  const handleScan = useCallback(() => {
    router.push('/group/scan' as any);
  }, []);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <TabHeader title="그룹" />

      <ScrollView style={styles.scroll} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* 그룹 진입 CTA 2종 — 만들기/참여를 동등한 선택지로 1:1 배치 */}
        <View style={styles.ctaRow}>
          <Pressable
            style={styles.ctaCard}
            onPress={handleCreate}
            accessibilityLabel="새 그룹 만들기"
            accessibilityRole="button"
          >
            <Feather name="plus-circle" size={scale(20)} color={colors.primaryBase} />
            <Text style={styles.ctaText}>새 그룹 만들기</Text>
          </Pressable>
          <Pressable
            style={styles.ctaCard}
            onPress={handleScan}
            accessibilityLabel="참여하기"
            accessibilityRole="button"
          >
            <Feather name="maximize" size={scale(20)} color={colors.labelNormal} />
            <Text style={styles.ctaText}>참여하기</Text>
          </Pressable>
        </View>

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
          <Text style={styles.sectionLabel}>모든 그룹 · {groups.length}</Text>
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
      </ScrollView>
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

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgAlt },
  scroll: { flex: 1 },
  content: { paddingVertical: space.s16, gap: space.s8, paddingBottom: 100 },
  loader: { marginTop: space.s40 },
  section: { paddingHorizontal: space.s16, gap: space.s10 },
  sectionLabel: {
    fontSize: scale(11), fontWeight: '700', color: colors.labelAlternative, letterSpacing: 0.06,
  },
  emptyText: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center', paddingVertical: space.s20 },
  errorBox: { margin: space.s16, padding: space.s16, borderRadius: radius.r12, backgroundColor: colors.bgNormal },
  errorText: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center' },
  ctaRow: { flexDirection: 'row', marginHorizontal: space.s16, gap: space.s12 },
  // 두 CTA 는 flex:1 로 화면 폭과 무관하게 서로 1:1 동일 크기를 유지한다(높이도 동일 고정).
  ctaCard: {
    flex: 1, height: scale(60),
    flexDirection: 'row',
    alignItems: 'center', justifyContent: 'center', gap: space.s8,
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    paddingHorizontal: space.s12, borderWidth: 1, borderColor: colors.line,
    ...shadows.small,
  },
  ctaText: { fontSize: scale(14), fontWeight: '600', color: colors.labelNormal, textAlign: 'center' },
});
