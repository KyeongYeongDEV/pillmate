import React, { useMemo } from 'react';
import { View, Text, ScrollView, StyleSheet, Pressable } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import PrescriptionDrugRow from '@/components/prescription/PrescriptionDrugRow';
import BootSkeleton from '@/components/common/BootSkeleton';
import { safeBack } from '@/lib/router/safeBack';
import {
  useGetGroupDetailQuery,
  useGetSharedPrescriptionQuery,
} from '@/store/slices/caregroupApi';
import type { SharedPrescriptionView } from '@/store/slices/caregroupApi';
import { scale, colors, typography, space, radius, shadows } from '@/styles/tokens';

const SHARE_DENIED_CODE = 'PILL_046';
const SHARE_DENIED_MSG = '이 구성원이 약 정보를 공유하지 않았어요';
const LOAD_FAILED_MSG = '약 정보를 불러올 수 없어요';
const EMPTY_DRUGS_MSG = '등록된 약 정보가 없어요';
const READ_ONLY_MSG = '보기 전용이에요. 공유받은 약 정보만 표시돼요';
const RETRY_LABEL = '다시 시도';

// 권한 없음(공유 안 함)은 재시도해도 결과가 같으므로 일반 오류와 구분해 재시도 버튼을 숨긴다.
function isShareDenied(error: unknown): boolean {
  const e = error as { status?: number; data?: { error?: { code?: string } } } | undefined;
  return e?.status === 403 && e?.data?.error?.code === SHARE_DENIED_CODE;
}

export default function SharedPrescriptionScreen() {
  const { id, prescriptionId, name } = useLocalSearchParams<{
    id: string; prescriptionId: string; name?: string;
  }>();
  const groupId = Number(id);
  const rxId = Number(prescriptionId);

  const { data: groupDetail } = useGetGroupDetailQuery(groupId, { skip: !Number.isFinite(groupId) });
  const {
    data, error, isLoading, isFetching, refetch,
  } = useGetSharedPrescriptionQuery(
    { groupId, prescriptionId: rxId },
    { skip: !Number.isFinite(groupId) || !Number.isFinite(rxId) },
  );

  const memberName = useMemo(
    () => name ?? (data ? groupDetail?.members.find(m => m.userId === data.ownerUserId)?.name : undefined),
    [name, data, groupDetail],
  );
  const title = memberName ? `${memberName}님의 약봉투` : '약봉투';

  if (isShareDenied(error)) {
    return <NoticeScreen title={title} groupId={groupId} message={SHARE_DENIED_MSG} />;
  }

  if (error) {
    return <NoticeScreen title={title} groupId={groupId} message={LOAD_FAILED_MSG} onRetry={() => refetch()} />;
  }

  // fulfilled 이전(첫 로딩)엔 "없어요"·"0종" 확정 문구 대신 스켈레톤 — 미공유·미복용 오해 방지(의료 P0).
  if (data === undefined && (isLoading || isFetching)) {
    return (
      <SafeAreaView style={styles.safe} edges={['top']}>
        <SharedHeader title={title} groupId={groupId} />
        <BootSkeleton />
      </SafeAreaView>
    );
  }

  const drugs = data?.drugs ?? [];

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <SharedHeader title={title} groupId={groupId} />

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {data && <LabelHeader label={data.label} prescribedAt={data.prescribedAt} />}
        {data && <InfoCard data={data} />}

        <Text style={styles.sectionLabel}>약 {drugs.length}종</Text>
        <View style={styles.drugList}>
          {drugs.map((drug, i) => (
            <PrescriptionDrugRow key={`${drug.nameRaw}-${i}`} drug={drug} />
          ))}
          {drugs.length === 0 && <Text style={styles.empty}>{EMPTY_DRUGS_MSG}</Text>}
        </View>

        <Text style={styles.readOnlyNote}>{READ_ONLY_MSG}</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

function LabelHeader({ label, prescribedAt }: { label: string | null; prescribedAt: string }) {
  return (
    <View style={styles.labelHeader}>
      <Text style={styles.labelHeaderDate}>{formatDate(prescribedAt)}</Text>
      {label ? <Text style={styles.labelHeaderText} numberOfLines={2}>{label}</Text> : null}
    </View>
  );
}

function InfoCard({ data }: { data: SharedPrescriptionView }) {
  const hasInfo = !!(data.status || data.periodStart || data.progressRate != null);
  if (!hasInfo) return null;

  return (
    <View style={styles.infoCard}>
      <StatusChip status={data.status} />
      {(data.periodStart || data.daysRemaining != null || data.adherenceRate != null) && (
        <View style={styles.infoPeriodRow}>
          <Text style={styles.infoPeriodTxt} numberOfLines={1}>{buildPeriodText(data)}</Text>
          <DayBadge data={data} />
        </View>
      )}
      {data.progressRate != null && <ProgressBar rate={data.progressRate} />}
    </View>
  );
}

function StatusChip({ status }: { status: 'ONGOING' | 'COMPLETED' }) {
  const isOngoing = status === 'ONGOING';
  return (
    <View style={[styles.chip, isOngoing ? styles.chipOngoing : styles.chipCompleted]}>
      <Text style={[styles.chipTxt, isOngoing ? styles.chipTxtOngoing : styles.chipTxtCompleted]}>
        {isOngoing ? '복용중' : '복용완료'}
      </Text>
    </View>
  );
}

function DayBadge({ data }: { data: SharedPrescriptionView }) {
  if (data.status === 'COMPLETED') {
    const pct = data.adherenceRate != null ? `${Math.round(data.adherenceRate * 100)}%` : '—';
    return <Text style={styles.adherenceTxt}>복약률 {pct}</Text>;
  }
  const d = data.daysRemaining;
  if (d == null) return null;
  if (d === 0) return <Text style={[styles.dDayTxt, styles.dDayUrgent]}>오늘 마지막</Text>;
  if (d === 1) return <Text style={[styles.dDayTxt, styles.dDayUrgent]}>내일 마지막</Text>;
  return <Text style={styles.dDayTxt}>D-{d}</Text>;
}

function ProgressBar({ rate }: { rate: number }) {
  const clamped = Math.min(1, Math.max(0, rate));
  return (
    <View style={styles.progressTrack}>
      {clamped > 0 && <View style={[styles.progressFill, { flex: clamped }]} />}
      <View style={{ flex: Math.max(0.001, 1 - clamped) }} />
    </View>
  );
}

function buildPeriodText(data: SharedPrescriptionView): string {
  const start = data.periodStart;
  const end = data.periodEnd;
  if (!start || !end) return '기간 미지정';
  const days = Math.round((new Date(end).getTime() - new Date(start).getTime()) / 86400000) + 1;
  return `${days}일분 · ${shortDate(start)} → ${shortDate(end)}`;
}

function shortDate(dateStr: string): string {
  const [, m, d] = dateStr.slice(0, 10).split('-');
  return `${parseInt(m, 10)}.${parseInt(d, 10)}`;
}

function formatDate(dateStr: string): string {
  const [y, m, d] = dateStr.slice(0, 10).split('-');
  return `${y}.${m}.${d}`;
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
      <SharedHeader title={title} groupId={groupId} />
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

function SharedHeader({ title, groupId }: { title: string; groupId: number }) {
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
      <Text style={styles.headerTitle} numberOfLines={1}>{title}</Text>
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
  headerTitle: { ...typography.headline1, color: colors.labelNormal, flex: 1, textAlign: 'center' },
  content: { padding: space.s16, gap: space.s16, paddingBottom: 40 },

  labelHeader: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, padding: space.s16,
    gap: space.s4, ...shadows.small,
  },
  labelHeaderDate: { ...typography.caption1, color: colors.labelAlternative },
  labelHeaderText: { fontSize: scale(20), fontWeight: '700', color: colors.labelNormal },

  infoCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, padding: space.s16,
    gap: space.s10, ...shadows.small,
  },
  infoPeriodRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space.s8 },
  infoPeriodTxt: { flex: 1, fontSize: scale(12), color: colors.labelAlternative },
  chip: { alignSelf: 'flex-start', paddingHorizontal: space.s10, paddingVertical: scale(3), borderRadius: radius.r8, borderWidth: 1 },
  chipOngoing: { borderColor: colors.statusPositive, backgroundColor: 'transparent' },
  chipCompleted: { borderColor: colors.line, backgroundColor: colors.bgAlt },
  chipTxt: { fontSize: scale(11), fontWeight: '700' },
  chipTxtOngoing: { color: colors.statusPositive },
  chipTxtCompleted: { color: colors.labelAlternative },
  adherenceTxt: { fontSize: scale(12), color: colors.labelAlternative },
  dDayTxt: { fontSize: scale(12), fontWeight: '700', color: colors.labelNormal },
  dDayUrgent: { color: colors.statusCautionary },
  progressTrack: {
    height: scale(4), backgroundColor: colors.bgAlt, borderRadius: radius.r4,
    overflow: 'hidden', flexDirection: 'row',
  },
  progressFill: { height: '100%', backgroundColor: colors.primaryNormal },

  sectionLabel: { fontSize: scale(13), fontWeight: '700', color: colors.labelAlternative },
  drugList: { gap: space.s8 },
  empty: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center', paddingVertical: space.s20 },
  readOnlyNote: {
    fontSize: scale(12), color: colors.labelAssistive,
    textAlign: 'center', marginTop: space.s8, paddingHorizontal: space.s16,
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
