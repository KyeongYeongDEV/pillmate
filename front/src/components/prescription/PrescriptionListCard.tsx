import React, { memo } from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Feather } from '@expo/vector-icons';
import type { PrescriptionSummary } from '@/types/prescription';
import { scale, colors, space, radius, shadows, typography } from '@/styles/tokens';

const PILL_COLORS = ['#5B9EF5', '#FF6B7A', '#7ECB7A', '#FFB84D', '#B57BF5'] as const;
const MAX_DOTS = 5;

interface Props {
  item: PrescriptionSummary;
  onPress: (id: number) => void;
}

function PrescriptionListCard({ item, onPress }: Props) {
  return (
    <Pressable
      style={styles.card}
      onPress={() => onPress(item.id)}
      accessibilityRole="button"
      accessibilityLabel={`${formatDate(item.prescribedAt)} 약봉투, 약 ${item.drugCount}종`}
    >
      <TopRow item={item} />
      <LabelRow item={item} />
      <PeriodRow item={item} />
      <ProgressBar rate={item.progressRate ?? 0} />
      {item.memo ? <MemoBox memo={item.memo} /> : null}
      <CardFooter item={item} />
    </Pressable>
  );
}

function TopRow({ item }: { item: PrescriptionSummary }) {
  const status = item.status ?? 'ONGOING';
  return (
    <View style={styles.topRow}>
      <StatusChip status={status} />
      <Text style={styles.prescribedAt}>{formatDate(item.prescribedAt)}</Text>
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

function LabelRow({ item }: { item: PrescriptionSummary }) {
  const hasLabel = !!item.label;
  const text = item.label ?? buildFallbackLabel(item);
  return (
    <Text style={[styles.labelTxt, !hasLabel && styles.labelFallbackTxt]} numberOfLines={1}>
      {text}
    </Text>
  );
}

function buildFallbackLabel(item: PrescriptionSummary): string {
  if (!item.drugNames) return `약 ${item.drugCount}종`;
  if (item.drugCount > 1) return `${item.drugNames} 외 ${item.drugCount - 1}종`;
  return item.drugNames;
}

function PeriodRow({ item }: { item: PrescriptionSummary }) {
  return (
    <View style={styles.periodRow}>
      <Text style={styles.periodTxt} numberOfLines={1}>{buildPeriodText(item)}</Text>
      <DayBadge item={item} />
    </View>
  );
}

function buildPeriodText(item: PrescriptionSummary): string {
  const start = item.periodStart ?? null;
  const end = item.periodEnd ?? null;
  if (!start || !end) return '기간 미지정';
  const days = Math.round((new Date(end).getTime() - new Date(start).getTime()) / 86400000) + 1;
  return `${days}일분 · ${formatShort(start)} → ${formatShort(end)}`;
}

function DayBadge({ item }: { item: PrescriptionSummary }) {
  const status = item.status ?? 'ONGOING';
  if (status === 'COMPLETED') {
    const pct = item.adherenceRate != null ? `${Math.round(item.adherenceRate * 100)}%` : '—';
    return <Text style={styles.adherenceTxt}>복약률 {pct}</Text>;
  }
  const d = item.daysRemaining ?? null;
  if (d == null) return null;
  if (d === 0) return <Text style={[styles.dDayTxt, styles.dDayUrgent]}>오늘 마지막</Text>;
  if (d === 1) return <Text style={[styles.dDayTxt, styles.dDayCautionary]}>내일 마지막</Text>;
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

function MemoBox({ memo }: { memo: string }) {
  return (
    <View style={styles.memoBox}>
      <Feather name="edit-2" size={scale(11)} color={colors.yellow40} />
      <Text style={styles.memoTxt} numberOfLines={2}>{memo}</Text>
    </View>
  );
}

function CardFooter({ item }: { item: PrescriptionSummary }) {
  const dotCount = Math.min(item.drugCount, MAX_DOTS);
  return (
    <View style={styles.footer}>
      <View style={styles.dots}>
        {Array.from({ length: dotCount }, (_, i) => (
          <View key={i} style={[styles.dot, { backgroundColor: PILL_COLORS[i % PILL_COLORS.length] }]} />
        ))}
      </View>
      <Text style={styles.drugCountTxt}>약 {item.drugCount}개</Text>
      <Text style={styles.detailTxt}>상세 {'>'}</Text>
    </View>
  );
}

function formatDate(dateStr: string): string {
  const [y, m, d] = dateStr.slice(0, 10).split('-');
  return `${y}.${m}.${d}`;
}

function formatShort(dateStr: string): string {
  const [, m, d] = dateStr.slice(0, 10).split('-');
  return `${parseInt(m, 10)}.${parseInt(d, 10)}`;
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16,
    borderWidth: 1,
    borderColor: colors.line,
    padding: space.s16,
    gap: space.s10,
    ...shadows.small,
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  chip: {
    paddingHorizontal: space.s10,
    paddingVertical: scale(3),
    borderRadius: radius.r8,
    borderWidth: 1,
  },
  chipOngoing: { borderColor: colors.statusPositive, backgroundColor: 'transparent' },
  chipCompleted: { borderColor: colors.line, backgroundColor: colors.bgAlt },
  chipTxt: { fontSize: scale(11), fontWeight: '700' },
  chipTxtOngoing: { color: colors.statusPositive },
  chipTxtCompleted: { color: colors.labelAlternative },
  prescribedAt: { ...typography.caption1, color: colors.labelAlternative },
  labelTxt: { ...typography.headline1, color: colors.labelNormal },
  labelFallbackTxt: { ...typography.body2r, color: colors.labelAlternative },
  periodRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space.s8,
  },
  periodTxt: { ...typography.caption1, color: colors.labelAlternative, flex: 1 },
  adherenceTxt: { ...typography.caption1, color: colors.labelAlternative },
  dDayTxt: { fontSize: scale(12), fontWeight: '700', color: colors.labelNormal },
  dDayUrgent: { color: colors.statusCautionary },
  dDayCautionary: { color: colors.statusCautionary },
  progressTrack: {
    height: scale(4),
    backgroundColor: colors.bgAlt,
    borderRadius: radius.r4,
    overflow: 'hidden',
    flexDirection: 'row',
  },
  progressFill: { height: '100%', backgroundColor: colors.primaryNormal },
  memoBox: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space.s6,
    backgroundColor: colors.yellow95,
    borderRadius: radius.r8,
    borderWidth: 1,
    borderColor: '#FDE68A',
    padding: space.s10,
  },
  memoTxt: {
    ...typography.caption1,
    color: colors.yellow40,
    flex: 1,
    lineHeight: scale(16),
  },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.s8,
    paddingTop: space.s4,
    borderTopWidth: 1,
    borderTopColor: colors.line,
  },
  dots: { flexDirection: 'row', gap: scale(4), alignItems: 'center' },
  dot: { width: scale(10), height: scale(10), borderRadius: radius.r8 },
  drugCountTxt: { ...typography.caption1, color: colors.labelAlternative, flex: 1 },
  detailTxt: { ...typography.caption1, color: colors.primaryNormal, fontWeight: '700' },
});

export default memo(PrescriptionListCard);
