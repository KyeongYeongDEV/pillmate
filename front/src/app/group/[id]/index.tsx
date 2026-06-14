import React, { useCallback } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable, ActivityIndicator, Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams, router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { useDispatch } from 'react-redux';
import AvatarStack from '@/components/common/AvatarStack';
import MemberCard from '@/components/group/MemberCard';
import InviteCodeCard from '@/components/group/InviteCodeCard';
import ActivityTimelineItem from '@/components/group/ActivityTimelineItem';
import { colors, space, radius, typography, shadows } from '@/styles/tokens';
import { useGetGroupDetailQuery, useIssueInviteCodeMutation, useLeaveGroupMutation, caregroupApiSlice } from '@/store/slices/caregroupApi';
import { useCountdown } from '@/hooks/useCountdown';
import { safeBack } from '@/lib/router/safeBack';
import type { GroupMember } from '@/types/group';
import type { MemberView } from '@/types/caregroup';

const ROLE_TINTS: Record<string, string> = {
  '환자': colors.patientOrange,
  '보호자': colors.guardianBlue,
  PATIENT: colors.patientOrange,
  GUARDIAN: colors.guardianBlue,
};

export default function GroupDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const groupId = Number(id);
  const dispatch = useDispatch();
  const { data: detail, isLoading, isError } = useGetGroupDetailQuery(groupId);
  const [issueInviteCode, { isLoading: isIssuing }] = useIssueInviteCodeMutation();
  const [leaveGroup, { isLoading: isLeaving }] = useLeaveGroupMutation();
  const expiresAt = detail?.inviteCode?.expiresAt ?? null;
  const { remainingSeconds, isExpired } = useCountdown(expiresAt);
  const inviteActive = !!detail?.inviteCode && !isExpired;

  const handleIssueInvite = async () => {
    try {
      await issueInviteCode(groupId).unwrap();
    } catch (e: any) {
      Alert.alert('초대 코드 발급 실패', e?.data?.error?.message ?? e?.message ?? '잠시 후 다시 시도해 주세요');
    }
  };

  const handleInviteExpire = useCallback(() => {
    dispatch(caregroupApiSlice.util.invalidateTags([{ type: 'GroupDetail', id: groupId }]));
  }, [dispatch, groupId]);

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
        <Header title="케어 그룹" />
        <ActivityIndicator size="large" color={colors.primaryBase} style={styles.loader} />
      </SafeAreaView>
    );
  }

  if (isError || !detail) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <Header title="케어 그룹" />
        <View style={styles.errorBox}>
          <Text style={styles.errorText}>그룹 정보를 불러올 수 없어요</Text>
        </View>
      </SafeAreaView>
    );
  }

  const memberNames = detail.members.map(m => m.name);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <Header title="케어 그룹" />
      <ScrollView style={styles.scroll} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* 그룹 카드 */}
        <View style={styles.heroCard}>
          <View style={styles.heroTop}>
            <AvatarStack names={memberNames} size={44} />
            <View style={styles.heroInfo}>
              <Text style={styles.heroName}>{detail.name}</Text>
              <Text style={styles.heroSub}>{detail.memberCount}명</Text>
            </View>
          </View>
          <View style={styles.inviteRow}>
            <Pressable
              style={[
                styles.inviteBtn,
                inviteActive && styles.inviteBtnIssued,
                isIssuing && styles.inviteBtnDisabled,
              ]}
              onPress={handleIssueInvite}
              disabled={isIssuing || inviteActive}
              accessibilityLabel={inviteActive ? `발급됨 ${remainingSeconds}초 남음` : '초대하기'}
              accessibilityRole="button"
              accessibilityState={{ disabled: isIssuing || inviteActive, busy: isIssuing }}
            >
              {isIssuing ? (
                <ActivityIndicator size="small" color={colors.staticWhite} />
              ) : inviteActive ? (
                <Text style={styles.inviteBtnIssuedText}>발급됨 · {remainingSeconds}초</Text>
              ) : (
                <>
                  <Feather name="plus" size={18} color={colors.staticWhite} />
                  <Text style={styles.inviteBtnText}>초대하기</Text>
                </>
              )}
            </Pressable>
            <Pressable
              style={styles.scanIconBtn}
              onPress={() => router.push('/group/scan' as any)}
              accessibilityLabel="QR 스캔으로 가입"
              accessibilityRole="button"
            >
              <Feather name="maximize" size={20} color={colors.labelNormal} />
            </Pressable>
          </View>

          {/* 초대 코드 (헤더 카드 안 — 초대하기 버튼 바로 밑) */}
          <InviteCodeCard
            inviteCode={inviteActive ? detail.inviteCode : null}
            onExpire={handleInviteExpire}
          />
        </View>

        {/* 구성원 */}
        <Text style={styles.sectionLabel}>구성원 · {detail.members.length}</Text>
        <View style={styles.listCard}>
          {detail.members.map((m, i) => (
            <MemberCard
              key={m.userId}
              member={memberViewToGroupMember(m)}
              isFirst={i === 0}
            />
          ))}
        </View>

        {/* 활동 타임라인 (상단 5건) */}
        <View style={styles.activityHeader}>
          <Text style={styles.activityTitle}>그룹 활동 (최근 일주일)</Text>
          <Pressable
            onPress={() => router.push(`/group/${groupId}/activity` as any)}
            accessibilityLabel="전체보기"
            accessibilityRole="button"
            hitSlop={8}
          >
            <Text style={styles.activityAll}>전체보기</Text>
          </Pressable>
        </View>
        <View>
          {detail.recentActivities.slice(0, 5).map((item, i, arr) => (
            <ActivityTimelineItem
              key={`${item.occurredAt}-${i}`}
              item={item}
              isLast={i === arr.length - 1}
            />
          ))}
          {detail.recentActivities.length === 0 && (
            <Text style={styles.emptyText}>최근 활동이 없어요</Text>
          )}
        </View>

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
              <Feather name="log-out" size={18} color={colors.statusNegative} />
              <Text style={styles.leaveBtnText}>그룹 나가기</Text>
            </>
          )}
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

function Header({ title }: { title: string }) {
  return (
    <View style={styles.header}>
      <Pressable
        onPress={() => safeBack('/(tabs)/group')}
        accessibilityLabel="뒤로가기"
        accessibilityRole="button"
        hitSlop={8}
      >
        <Feather name="chevron-left" size={24} color={colors.labelNormal} />
      </Pressable>
      <Text style={styles.headerTitle}>{title}</Text>
      <Pressable accessibilityLabel="그룹 설정" accessibilityRole="button" hitSlop={8}>
        <Feather name="settings" size={20} color={colors.labelNormal} />
      </Pressable>
    </View>
  );
}

function memberViewToGroupMember(m: MemberView): GroupMember {
  const roleLabel = m.role === 'PATIENT' ? '환자' : m.role === 'GUARDIAN' ? '보호자' : m.role;
  return {
    id: String(m.userId),
    name: m.name,
    sub: roleLabel,
    role: roleLabel as GroupMember['role'],
    tint: ROLE_TINTS[m.role] ?? colors.fallbackGray,
    online: false,
  };
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
  heroCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r20,
    padding: space.s20, borderWidth: 1, borderColor: colors.line, gap: space.s16,
    ...shadows.small,
  },
  heroTop: { flexDirection: 'row', alignItems: 'center', gap: space.s14 },
  heroInfo: { flex: 1, marginLeft: space.s8 },
  heroName: { fontSize: 18, fontWeight: '700', letterSpacing: -0.015, color: colors.labelNormal },
  heroSub: { fontSize: 13, color: colors.labelAlternative, marginTop: 2 },
  inviteRow: { flexDirection: 'row', alignItems: 'center', gap: space.s10 },
  inviteBtn: {
    flex: 1,
    height: 42, borderRadius: radius.r10,
    backgroundColor: colors.labelNormal, flexDirection: 'row',
    alignItems: 'center', justifyContent: 'center', gap: space.s6,
  },
  inviteBtnText: { fontSize: 14, fontWeight: '600', color: colors.staticWhite },
  inviteBtnDisabled: { opacity: 0.6 },
  inviteBtnIssued: { backgroundColor: colors.fillNormal },
  inviteBtnIssuedText: { fontSize: 14, fontWeight: '600', color: colors.labelAlternative },
  scanIconBtn: {
    width: 42, height: 42, borderRadius: radius.r10,
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: colors.fillNormal,
  },
  sectionLabel: { fontSize: 11, fontWeight: '700', color: colors.labelAlternative, letterSpacing: 0.06 },
  listCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, overflow: 'hidden',
  },
  activityHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  activityTitle: { fontSize: 13, fontWeight: '700', color: colors.labelNormal },
  activityAll: { fontSize: 12, color: colors.primaryBase, fontWeight: '600' },
  emptyText: { fontSize: 14, color: colors.labelAlternative, textAlign: 'center', paddingVertical: space.s20 },
  errorBox: { margin: space.s16, padding: space.s16, borderRadius: radius.r12, backgroundColor: colors.bgNormal },
  errorText: { fontSize: 14, color: colors.labelAlternative, textAlign: 'center' },
  leaveBtn: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.s6,
    height: 48, borderRadius: radius.r12,
    backgroundColor: colors.bgNormal, borderWidth: 1, borderColor: colors.statusNegative,
  },
  leaveBtnDisabled: { opacity: 0.6 },
  leaveBtnText: { fontSize: 14, fontWeight: '600', color: colors.statusNegative },
});
