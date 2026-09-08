import React, { useCallback, useState } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable, ActivityIndicator, Alert, TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams, router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { scale, colors, space, radius, typography, shadows } from '@/styles/tokens';
import {
  useGetGroupDetailQuery,
  useRenameGroupMutation,
  useLeaveGroupMutation,
} from '@/store/slices/caregroupApi';
import { safeBack } from '@/lib/router/safeBack';

const NAME_MAX_LENGTH = 100;

export default function GroupSettingsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const groupId = Number(id);
  const { data: detail, isLoading, isError } = useGetGroupDetailQuery(groupId);
  const [renameGroup, { isLoading: isRenaming }] = useRenameGroupMutation();
  const [leaveGroup, { isLoading: isLeaving }] = useLeaveGroupMutation();
  const [name, setName] = useState<string | null>(null);

  const originalName = detail?.name ?? '';
  const value = name ?? originalName;
  const trimmed = value.trim();
  const canSave = trimmed.length > 0 && trimmed !== originalName.trim() && !isRenaming;

  const handleSave = useCallback(async () => {
    try {
      await renameGroup({ groupId, name: trimmed }).unwrap();
      Alert.alert('그룹 이름을 변경했어요');
      safeBack(`/group/${groupId}`);
    } catch (e: any) {
      const status = e?.status;
      Alert.alert(
        '그룹 이름 변경 실패',
        status === 403
          ? '이 그룹의 설정을 변경할 수 없어요'
          : e?.data?.error?.message ?? '잠시 후 다시 시도해 주세요',
      );
    }
  }, [renameGroup, groupId, trimmed]);

  const confirmLeave = useCallback(async () => {
    try {
      await leaveGroup(groupId).unwrap();
      router.replace('/(tabs)/group');
    } catch (e: any) {
      Alert.alert('그룹 나가기 실패', e?.data?.error?.message ?? e?.message ?? '잠시 후 다시 시도해 주세요');
    }
  }, [leaveGroup, groupId]);

  const handleLeave = useCallback(() => {
    Alert.alert(
      '그룹 나가기',
      '이 그룹에서 나가시겠어요? 나가면 이 그룹의 복약 정보·알림을 더 이상 받을 수 없어요.',
      [
        { text: '취소', style: 'cancel' },
        { text: '나가기', style: 'destructive', onPress: confirmLeave },
      ],
    );
  }, [confirmLeave]);

  if (isLoading) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <Header groupId={groupId} />
        <ActivityIndicator size="large" color={colors.primaryBase} style={styles.loader} />
      </SafeAreaView>
    );
  }

  if (isError || !detail) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <Header groupId={groupId} />
        <View style={styles.errorBox}>
          <Text style={styles.errorText}>그룹 정보를 불러올 수 없어요</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <Header groupId={groupId} />
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <Text style={styles.sectionLabel}>그룹 이름</Text>
        <View style={styles.card}>
          <TextInput
            style={styles.input}
            value={value}
            onChangeText={setName}
            maxLength={NAME_MAX_LENGTH}
            placeholder="그룹 이름"
            placeholderTextColor={colors.labelAlternative}
            accessibilityLabel="그룹 이름"
          />
        </View>
        <Pressable
          style={[styles.saveBtn, !canSave && styles.saveBtnDisabled]}
          onPress={handleSave}
          disabled={!canSave}
          accessibilityLabel="저장"
          accessibilityRole="button"
          accessibilityState={{ disabled: !canSave, busy: isRenaming }}
        >
          {isRenaming ? (
            <ActivityIndicator size="small" color={colors.staticWhite} />
          ) : (
            <Text style={styles.saveBtnText}>저장</Text>
          )}
        </Pressable>

        <Pressable
          style={[styles.leaveBtn, isLeaving && styles.leaveBtnDisabled]}
          onPress={handleLeave}
          disabled={isLeaving}
          accessibilityLabel="그룹 나가기"
          accessibilityRole="button"
          accessibilityState={{ disabled: isLeaving, busy: isLeaving }}
        >
          {isLeaving ? (
            <ActivityIndicator size="small" color={colors.statusNegative} />
          ) : (
            <>
              <Feather name="log-out" size={scale(18)} color={colors.statusNegative} />
              <Text style={styles.leaveBtnText}>그룹 나가기</Text>
            </>
          )}
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

function Header({ groupId }: { groupId: number }) {
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
      <Text style={styles.headerTitle}>그룹 설정</Text>
      <View style={{ width: scale(24) }} />
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgAlt },
  loader: { flex: 1, marginTop: space.s40 },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal, borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  scroll: { flex: 1 },
  content: { padding: space.s16, gap: space.s12, paddingBottom: 80 },
  sectionLabel: {
    fontSize: scale(11), fontWeight: '700', color: colors.labelAlternative,
    letterSpacing: 0.06, marginTop: space.s4,
  },
  card: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, overflow: 'hidden',
    ...shadows.small,
  },
  input: {
    fontSize: scale(15), fontWeight: '700', color: colors.labelNormal,
    letterSpacing: -0.01, padding: space.s14,
  },
  saveBtn: {
    height: scale(48), borderRadius: radius.r12,
    backgroundColor: colors.labelNormal,
    alignItems: 'center', justifyContent: 'center',
  },
  saveBtnDisabled: { opacity: 0.4 },
  saveBtnText: { fontSize: scale(14), fontWeight: '600', color: colors.staticWhite },
  leaveBtn: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.s6,
    height: scale(48), borderRadius: radius.r12, marginTop: space.s16,
    backgroundColor: colors.bgNormal, borderWidth: 1, borderColor: colors.statusNegative,
  },
  leaveBtnDisabled: { opacity: 0.6 },
  leaveBtnText: { fontSize: scale(14), fontWeight: '600', color: colors.statusNegative },
  errorBox: { margin: space.s16, padding: space.s16, borderRadius: radius.r12, backgroundColor: colors.bgNormal },
  errorText: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center' },
});
