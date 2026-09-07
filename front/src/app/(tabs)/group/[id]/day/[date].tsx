import React, { useMemo } from 'react';
import { View, Text, ScrollView, StyleSheet, Pressable } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import MedTimeRow from '@/components/schedule/MedTimeRow';
import BootSkeleton from '@/components/common/BootSkeleton';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import { safeBack } from '@/lib/router/safeBack';
import { assignMemberColors } from '@/utils/memberColors';
import { getKstToday } from '@/utils/calendarUtils';
import { useGetGroupDetailQuery, useGetGroupDayScheduleQuery } from '@/store/slices/caregroupApi';
import type { GroupMemberDayView } from '@/store/slices/caregroupApi';

const FORBIDDEN_STATUS = 403;
const FORBIDDEN_MSG = '이 날짜의 그룹 복약 정보를 볼 수 없어요';
const LOAD_FAILED_MSG = '그룹 복약 정보를 불러올 수 없어요';
const RETRY_LABEL = '재시도';
const READ_ONLY_MSG = '보기 전용이에요. 복약 체크는 본인만 할 수 있어요';
const NO_MEMBERS_MSG = '이 날짜에 그룹 구성원의 복약 정보가 없어요';
const NO_SLOTS_MSG = '이 날짜에 등록된 복약이 없어요';

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function resolveDate(date?: string): string {
  return typeof date === 'string' && DATE_PATTERN.test(date) ? date : getKstToday();
}

function formatDayTitle(dateStr: string): string {
  const [, month, day] = dateStr.split('-').map(Number);
  return `${month}월 ${day}일 그룹 복약`;
}

function isForbidden(error: unknown): boolean {
  return (error as { status?: number } | undefined)?.status === FORBIDDEN_STATUS;
}

export default function GroupDayScheduleScreen() {
  const { id, date } = useLocalSearchParams<{ id: string; date?: string }>();
  const groupId = Number(id);
  const targetDate = useMemo(() => resolveDate(date), [date]);
  const title = useMemo(() => formatDayTitle(targetDate), [targetDate]);

  const { data: groupDetail } = useGetGroupDetailQuery(groupId, { skip: !Number.isFinite(groupId) });
  const {
    data: members, error, isLoading, isFetching, refetch,
  } = useGetGroupDayScheduleQuery(
    { groupId, date: targetDate },
    { skip: !Number.isFinite(groupId) },
  );

  const colorByUserId = useMemo(
    () => assignMemberColors(groupDetail?.members ?? []),
    [groupDetail],
  );

  if (isForbidden(error)) {
    return <NoticeScreen title={title} groupId={groupId} message={FORBIDDEN_MSG} />;
  }

  if (error) {
    return (
      <NoticeScreen title={title} groupId={groupId} message={LOAD_FAILED_MSG} onRetry={() => refetch()} />
    );
  }

  // fulfilled 이전(첫 로딩)엔 "없어요"·"0/0" 확정 문구 대신 스켈레톤 — 보호자의 미복용 오해 방지(의료 P0).
  const ready = members !== undefined;
  if (!ready && (isLoading || isFetching)) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <DayHeader title={title} groupId={groupId} />
        <BootSkeleton />
      </SafeAreaView>
    );
  }

  const dayMembers: GroupMemberDayView[] = members ?? [];

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <DayHeader title={title} groupId={groupId} />

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        {dayMembers.length === 0 ? (
          <Text style={styles.emptyText}>{NO_MEMBERS_MSG}</Text>
        ) : (
          dayMembers.map((member, index) => (
            <MemberSection
              key={member.userId}
              member={member}
              color={colorByUserId.get(member.userId) ?? colors.fallbackGray}
              isFirst={index === 0}
            />
          ))
        )}

        {dayMembers.length > 0 && <Text style={styles.readOnlyNote}>{READ_ONLY_MSG}</Text>}
      </ScrollView>
    </SafeAreaView>
  );
}

function MemberSection({
  member, color, isFirst,
}: {
  member: GroupMemberDayView;
  color: string;
  isFirst: boolean;
}) {
  const { schedule } = member;
  const slots = schedule.slots;

  return (
    <View style={[styles.section, !isFirst && styles.sectionDivider]}>
      <View style={styles.sectionHeader}>
        <View style={styles.sectionName}>
          <View testID={`member-dot-${member.userId}`} style={[styles.memberDot, { backgroundColor: color }]} />
          <Text style={styles.memberName}>{member.name}</Text>
        </View>
        <Text style={styles.memberCount}>복약 {schedule.doneCount} / {schedule.totalCount} 완료</Text>
      </View>

      <View style={styles.medCard}>
        {slots.map((slot, i) => (
          <MedTimeRow key={`${slot.id}-${i}`} slot={slot} isFirst={i === 0} readOnly />
        ))}
        {slots.length === 0 && <Text style={styles.emptyText}>{NO_SLOTS_MSG}</Text>}
      </View>
    </View>
  );
}

function NoticeScreen({
  title, groupId, message, onRetry,
}: {
  title: string;
  groupId: number;
  message: string;
  onRetry?: () => void;
}) {
  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <DayHeader title={title} groupId={groupId} />
      <View style={styles.noticeBox}>
        <Text style={styles.noticeText}>{message}</Text>
        {onRetry && (
          <Pressable
            style={styles.retryBtn}
            onPress={onRetry}
            accessibilityLabel={RETRY_LABEL}
            accessibilityRole="button"
          >
            <Text style={styles.retryTxt}>{RETRY_LABEL}</Text>
          </Pressable>
        )}
      </View>
    </SafeAreaView>
  );
}

function DayHeader({ title, groupId }: { title: string; groupId: number }) {
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
      <Text style={styles.headerTitle}>{title}</Text>
      <View style={{ width: scale(24) }} />
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal, borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  scroll: { flex: 1 },
  content: { paddingVertical: space.s20, paddingBottom: 80 },
  section: { paddingHorizontal: space.s16 },
  sectionDivider: { borderTopWidth: scale(8), borderTopColor: colors.bgAlt, paddingTop: space.s20, marginTop: space.s20 },
  sectionHeader: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    marginBottom: space.s12,
  },
  sectionName: { flexDirection: 'row', alignItems: 'center', gap: space.s8 },
  memberDot: { width: scale(10), height: scale(10), borderRadius: scale(5) },
  memberName: { ...typography.headline1, color: colors.labelNormal },
  memberCount: { fontSize: scale(12), color: colors.labelAlternative, fontWeight: '600' },
  medCard: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16, borderWidth: 1, borderColor: colors.line, overflow: 'hidden',
  },
  emptyText: {
    fontSize: scale(13), color: colors.labelAlternative,
    textAlign: 'center', paddingVertical: space.s24,
  },
  readOnlyNote: {
    fontSize: scale(12), color: colors.labelAssistive,
    textAlign: 'center', marginTop: space.s20, paddingHorizontal: space.s16,
  },
  noticeBox: {
    margin: space.s16, padding: space.s20, borderRadius: radius.r12,
    backgroundColor: colors.bgNormal, alignItems: 'center', gap: space.s16,
  },
  noticeText: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center' },
  retryBtn: {
    paddingHorizontal: space.s20, paddingVertical: space.s10,
    borderRadius: radius.r12, backgroundColor: colors.primaryNormal,
  },
  retryTxt: { ...typography.label2, fontWeight: '700', color: '#fff' },
});
