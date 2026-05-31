import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Avatar from '@/components/common/Avatar';
import { colors, space, radius } from '@/styles/tokens';
import type { ActivityView } from '@/types/caregroup';

const ACTIVITY_TINTS: Record<string, string> = {
  DOSE_TAKEN: colors.statusPositive,
  DOSE_MISSED: colors.statusNegative,
  PRESCRIPTION_ADDED: colors.primaryBase,
};

const ACTIVITY_LABEL: Record<string, string> = {
  DOSE_TAKEN: '복용',
  DOSE_MISSED: '미복용',
  PRESCRIPTION_ADDED: '처방전',
};

interface ActivityTimelineItemProps {
  item: ActivityView;
  isLast?: boolean;
}

function ActivityTimelineItem({ item, isLast }: ActivityTimelineItemProps) {
  const dotColor = ACTIVITY_TINTS[item.activityType] ?? colors.labelAlternative;
  const label = ACTIVITY_LABEL[item.activityType] ?? item.activityType;

  return (
    <View style={styles.wrapper}>
      <View style={styles.rail}>
        {!isLast && <View style={styles.line} />}
        <View style={[styles.dot, { backgroundColor: dotColor }]} />
      </View>
      <View style={[styles.card, isLast ? styles.cardLast : styles.cardSpaced]}>
        <View style={styles.head}>
          <Avatar name={item.actorName[0] ?? '?'} tint={dotColor} size={24} />
          <Text style={styles.actor}>{item.actorName}</Text>
          <View style={[styles.chip, { backgroundColor: `${dotColor}20` }]}>
            <Text style={[styles.chipText, { color: dotColor }]}>{label}</Text>
          </View>
          <Text style={styles.time}>{formatRelativeTime(item.occurredAt)}</Text>
        </View>
        <Text style={styles.summary}>{item.summary}</Text>
      </View>
    </View>
  );
}

function formatRelativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 1) return '방금';
  if (mins < 60) return `${mins}분 전`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}시간 전`;
  return `${Math.floor(hrs / 24)}일 전`;
}

export default React.memo(ActivityTimelineItem);

const styles = StyleSheet.create({
  wrapper: { flexDirection: 'row', gap: space.s12 },
  rail: { width: 14, alignItems: 'center', paddingTop: 14 },
  line: { position: 'absolute', top: 18, bottom: -2, width: 2, backgroundColor: colors.line, left: 6 },
  dot: { width: 10, height: 10, borderRadius: 5 },
  card: {
    flex: 1, backgroundColor: colors.bgNormal,
    borderRadius: radius.r12, padding: space.s12,
    borderWidth: 1, borderColor: colors.line, gap: space.s6,
  },
  cardSpaced: { marginBottom: space.s10 },
  cardLast: {},
  head: { flexDirection: 'row', alignItems: 'center', gap: space.s6 },
  actor: { flex: 1, fontSize: 13, fontWeight: '700', color: colors.labelNormal },
  chip: { paddingHorizontal: space.s6, paddingVertical: 2, borderRadius: radius.r4 },
  chipText: { fontSize: 10, fontWeight: '600' },
  time: { fontSize: 11, color: colors.labelAlternative },
  summary: { fontSize: 13, color: colors.labelAlternative, lineHeight: 18 },
});
