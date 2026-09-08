import React, { useCallback, useState } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable, ActivityIndicator, Alert, RefreshControl, Switch,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { scale, colors, space, radius, typography, shadows } from '@/styles/tokens';
import {
  useGetShareSettingsQuery,
  useUpdateMemberShareMutation,
  useUpdatePrescriptionShareMutation,
  type ShareMemberSetting,
  type SharePrescriptionSetting,
} from '@/store/slices/caregroupApi';
import { safeBack } from '@/lib/router/safeBack';

function formatDate(dateStr: string): string {
  const [y, m, d] = dateStr.slice(0, 10).split('-');
  return `${y}.${m}.${d}`;
}

function roleLabel(role: string): string {
  return role === 'PATIENT' ? '환자' : role === 'GUARDIAN' ? '보호자' : role;
}

function prescriptionLabel(item: SharePrescriptionSetting): string {
  return item.label ?? `약봉투 #${item.prescriptionId}`;
}

export default function ShareSettingsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const groupId = Number(id);
  const { data, isLoading, isError, error, refetch } = useGetShareSettingsQuery(groupId);
  const [updateMemberShare] = useUpdateMemberShareMutation();
  const [updatePrescriptionShare] = useUpdatePrescriptionShareMutation();
  const [pendingMembers, setPendingMembers] = useState<number[]>([]);
  const [pendingPrescriptions, setPendingPrescriptions] = useState<number[]>([]);
  const [refreshing, setRefreshing] = useState(false);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await refetch();
    } finally {
      setRefreshing(false);
    }
  }, [refetch]);

  const handleMemberToggle = useCallback(async (viewerUserId: number, enabled: boolean) => {
    if (pendingMembers.includes(viewerUserId)) return;
    setPendingMembers((p) => [...p, viewerUserId]);
    try {
      await updateMemberShare({ groupId, viewerUserId, enabled }).unwrap();
    } catch (e: any) {
      Alert.alert('공유 설정 변경 실패', e?.data?.error?.message ?? '잠시 후 다시 시도해 주세요');
    } finally {
      setPendingMembers((p) => p.filter((uid) => uid !== viewerUserId));
    }
  }, [pendingMembers, updateMemberShare, groupId]);

  const handlePrescriptionToggle = useCallback(async (prescriptionId: number, enabled: boolean) => {
    if (pendingPrescriptions.includes(prescriptionId)) return;
    setPendingPrescriptions((p) => [...p, prescriptionId]);
    try {
      await updatePrescriptionShare({ groupId, prescriptionId, enabled }).unwrap();
    } catch (e: any) {
      Alert.alert('공유 설정 변경 실패', e?.data?.error?.message ?? '잠시 후 다시 시도해 주세요');
    } finally {
      setPendingPrescriptions((p) => p.filter((pid) => pid !== prescriptionId));
    }
  }, [pendingPrescriptions, updatePrescriptionShare, groupId]);

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

  const members = data?.members ?? [];
  const prescriptions = data?.prescriptions ?? [];

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <Header />
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primaryBase} />}
      >
        <Text style={styles.intro}>구성원과 약봉투를 모두 켜야 상대가 그 약 이름을 볼 수 있어요.</Text>

        <Text style={styles.sectionLabel}>공유할 구성원</Text>
        {members.length === 0 ? (
          <EmptyCard text="함께하는 구성원이 없어요" />
        ) : (
          <View style={styles.listCard}>
            {members.map((member, i) => (
              <MemberShareRow
                key={member.userId}
                member={member}
                isFirst={i === 0}
                disabled={pendingMembers.includes(member.userId)}
                onToggle={handleMemberToggle}
              />
            ))}
          </View>
        )}

        <Text style={styles.sectionLabel}>공유할 약봉투</Text>
        {prescriptions.length === 0 ? (
          <EmptyCard text="현재 복용중인 약봉투가 없어요" />
        ) : (
          <View style={styles.listCard}>
            {prescriptions.map((item, i) => (
              <PrescriptionShareRow
                key={item.prescriptionId}
                item={item}
                isFirst={i === 0}
                disabled={pendingPrescriptions.includes(item.prescriptionId)}
                onToggle={handlePrescriptionToggle}
              />
            ))}
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function MemberShareRow({
  member, isFirst, disabled, onToggle,
}: {
  member: ShareMemberSetting;
  isFirst: boolean;
  disabled: boolean;
  onToggle: (viewerUserId: number, enabled: boolean) => void;
}) {
  return (
    <View style={[styles.row, !isFirst && styles.borderTop]}>
      <View style={styles.info}>
        <Text style={styles.name} numberOfLines={1}>{member.name}</Text>
        <Text style={styles.sub}>{roleLabel(member.role)}</Text>
      </View>
      <Switch
        value={member.shared}
        disabled={disabled}
        onValueChange={(next) => onToggle(member.userId, next)}
        trackColor={{ true: colors.primaryBase, false: colors.lineSolidNorm }}
        thumbColor={colors.staticWhite}
        ios_backgroundColor={colors.lineSolidNorm}
        accessibilityLabel={`${member.name}에게 공유`}
      />
    </View>
  );
}

function PrescriptionShareRow({
  item, isFirst, disabled, onToggle,
}: {
  item: SharePrescriptionSetting;
  isFirst: boolean;
  disabled: boolean;
  onToggle: (prescriptionId: number, enabled: boolean) => void;
}) {
  const name = prescriptionLabel(item);
  return (
    <View style={[styles.row, !isFirst && styles.borderTop]}>
      <View style={styles.info}>
        <Text style={styles.name} numberOfLines={1}>{name}</Text>
        <Text style={styles.sub}>{formatDate(item.prescribedAt)}</Text>
      </View>
      <Switch
        value={item.shared}
        disabled={disabled}
        onValueChange={(next) => onToggle(item.prescriptionId, next)}
        trackColor={{ true: colors.primaryBase, false: colors.lineSolidNorm }}
        thumbColor={colors.staticWhite}
        ios_backgroundColor={colors.lineSolidNorm}
        accessibilityLabel={`${name} 그룹에 공유`}
      />
    </View>
  );
}

function EmptyCard({ text }: { text: string }) {
  return (
    <View style={styles.emptyBox}>
      <Text style={styles.emptyText}>{text}</Text>
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
  content: { padding: space.s16, gap: space.s12, paddingBottom: 80 },
  intro: { fontSize: scale(13), color: colors.labelAlternative, lineHeight: scale(19) },
  sectionLabel: {
    fontSize: scale(11), fontWeight: '700', color: colors.labelAlternative,
    letterSpacing: 0.06, marginTop: space.s4,
  },
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
  sub: { fontSize: scale(12), color: colors.labelAlternative, marginTop: 2 },
  emptyBox: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, paddingVertical: space.s40, alignItems: 'center',
  },
  emptyText: { fontSize: scale(14), color: colors.labelAlternative },
  errorBox: { margin: space.s16, padding: space.s16, borderRadius: radius.r12, backgroundColor: colors.bgNormal },
  errorText: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center' },
});
