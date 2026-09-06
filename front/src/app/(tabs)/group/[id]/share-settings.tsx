import React, { useCallback, useState } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable, ActivityIndicator, Alert, RefreshControl, Switch,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import Avatar from '@/components/common/Avatar';
import { scale, colors, space, radius, typography, shadows } from '@/styles/tokens';
import { useGetShareSettingsQuery, useUpdateShareSettingMutation } from '@/store/slices/caregroupApi';
import { safeBack } from '@/lib/router/safeBack';
import type { ShareSettingView } from '@/types/caregroup';

const ROLE_TINTS: Record<string, string> = {
  '환자': colors.patientOrange,
  '보호자': colors.guardianBlue,
  PATIENT: colors.patientOrange,
  GUARDIAN: colors.guardianBlue,
};

function roleLabel(role: string): string {
  if (role === 'PATIENT') return '환자';
  if (role === 'GUARDIAN') return '보호자';
  return role;
}

export default function ShareSettingsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const groupId = Number(id);
  const { data: members, isLoading, isError, error, refetch } = useGetShareSettingsQuery(groupId);
  const [updateShareSetting] = useUpdateShareSettingMutation();
  const [pending, setPending] = useState<number[]>([]);
  const [refreshing, setRefreshing] = useState(false);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await refetch();
    } finally {
      setRefreshing(false);
    }
  }, [refetch]);

  const handleToggle = useCallback(async (viewerUserId: number, enabled: boolean) => {
    if (pending.includes(viewerUserId)) return;
    setPending((p) => [...p, viewerUserId]);
    try {
      await updateShareSetting({ groupId, viewerUserId, enabled }).unwrap();
    } catch (e: any) {
      Alert.alert('공유 설정 변경 실패', e?.data?.error?.message ?? '잠시 후 다시 시도해 주세요');
    } finally {
      setPending((p) => p.filter((memberId) => memberId !== viewerUserId));
    }
  }, [pending, updateShareSetting, groupId]);

  if (isLoading) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <Header />
        <ActivityIndicator size="large" color={colors.primaryBase} style={styles.loader} />
      </SafeAreaView>
    );
  }

  if (isError) {
    const isForbidden = (error as { status?: number })?.status === 403;
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <Header />
        <View style={styles.errorBox}>
          <Text style={styles.errorText}>
            {isForbidden ? '이 그룹의 공유 설정에 접근할 수 없어요' : '공유 설정을 불러올 수 없어요'}
          </Text>
        </View>
      </SafeAreaView>
    );
  }

  const list = members ?? [];

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <Header />
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primaryBase} />}
      >
        <Text style={styles.intro}>켠 구성원만 내 약 이름을 볼 수 있어요.</Text>

        {list.length === 0 ? (
          <View style={styles.emptyBox}>
            <Text style={styles.emptyText}>공유할 구성원이 없어요</Text>
          </View>
        ) : (
          <View style={styles.listCard}>
            {list.map((member, i) => (
              <ShareRow
                key={member.userId}
                member={member}
                isFirst={i === 0}
                disabled={pending.includes(member.userId)}
                onToggle={handleToggle}
              />
            ))}
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function ShareRow({
  member, isFirst, disabled, onToggle,
}: {
  member: ShareSettingView;
  isFirst: boolean;
  disabled: boolean;
  onToggle: (viewerUserId: number, enabled: boolean) => void;
}) {
  const label = roleLabel(member.role);
  return (
    <View style={[styles.row, !isFirst && styles.borderTop]}>
      <Avatar name={member.name[0]} tint={ROLE_TINTS[member.role] ?? colors.fallbackGray} size={scale(44)} />
      <View style={styles.info}>
        <Text style={styles.name}>{member.name}</Text>
        <Text style={styles.role}>{label}</Text>
      </View>
      <Switch
        value={member.shared}
        disabled={disabled}
        onValueChange={(next) => onToggle(member.userId, next)}
        trackColor={{ true: colors.primaryBase, false: colors.lineSolidNorm }}
        thumbColor={colors.staticWhite}
        ios_backgroundColor={colors.lineSolidNorm}
        accessibilityLabel={`${member.name}에게 약 정보 공유`}
      />
    </View>
  );
}

function Header() {
  return (
    <View style={styles.header}>
      <Pressable
        onPress={() => safeBack('/(tabs)/group')}
        accessibilityLabel="뒤로가기"
        accessibilityRole="button"
        hitSlop={8}
      >
        <Feather name="chevron-left" size={scale(24)} color={colors.labelNormal} />
      </Pressable>
      <Text style={styles.headerTitle}>알약 정보 공유</Text>
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
  content: { padding: space.s16, gap: space.s16, paddingBottom: 80 },
  intro: { fontSize: scale(13), color: colors.labelAlternative, lineHeight: scale(19) },
  listCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, overflow: 'hidden',
    ...shadows.small,
  },
  row: {
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
    padding: space.s14,
  },
  borderTop: { borderTopWidth: 1, borderTopColor: colors.line },
  info: { flex: 1 },
  name: { fontSize: scale(15), fontWeight: '700', color: colors.labelNormal, letterSpacing: -0.01 },
  role: { fontSize: scale(12), color: colors.labelAlternative, marginTop: 2 },
  emptyBox: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, paddingVertical: space.s40, alignItems: 'center',
  },
  emptyText: { fontSize: scale(14), color: colors.labelAlternative },
  errorBox: { margin: space.s16, padding: space.s16, borderRadius: radius.r12, backgroundColor: colors.bgNormal },
  errorText: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center' },
});
