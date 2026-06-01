import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import Avatar from '@/components/common/Avatar';
import { colors, space, radius } from '@/styles/tokens';
import type { ActivityView } from '@/types/caregroup';

interface DotColorMap {
  [key: string]: string;
}

const DOT_COLOR: DotColorMap = {
  DOSE_TAKEN: colors.statusPositive,
  DOSE_MISSED: colors.statusNegative,
  AI_INSIGHT: colors.violet45,
  AI_REPORT: colors.violet45,
  PRESCRIPTION_ADDED: colors.primaryBase,
  COMMENT: colors.cyan50,
  MEMBER_JOINED: colors.pink46,
};

const DEFAULT_DOT = colors.labelAlternative;

interface ActivityTimelineItemProps {
  item: ActivityView;
  isLast?: boolean;
  whoLabel?: string;
}

function ActivityTimelineItem({ item, isLast, whoLabel }: ActivityTimelineItemProps) {
  const dotColor = DOT_COLOR[item.activityType] ?? DEFAULT_DOT;
  const isMiss = item.activityType === 'DOSE_MISSED';

  return (
    <View style={styles.wrapper}>
      <View style={styles.rail}>
        {!isLast && <View style={styles.line} />}
        <View style={[styles.dot, { backgroundColor: dotColor }]} />
      </View>
      <View style={[styles.card, isLast ? styles.cardLast : styles.cardSpaced]}>
        <View style={styles.head}>
          <Avatar name={item.actorName[0] ?? '?'} tint={dotColor} size={28} />
          <View style={styles.headTextCol}>
            <Text style={styles.actor}>
              <Text style={styles.actorName}>{item.actorName}</Text>
              {whoLabel && <Text style={styles.actorLabel}> · {whoLabel}</Text>}
            </Text>
          </View>
          <Text style={styles.time}>{formatRelativeTime(item.occurredAt)}</Text>
        </View>

        <View style={styles.titleRow}>
          {isMiss && (
            <View style={styles.warnBadge}>
              <Feather name="alert-triangle" size={11} color={colors.red40} />
            </View>
          )}
          <Text style={styles.title}>{item.summary}</Text>
        </View>
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
  wrapper: { flexDirection: 'row', gap: space.s14 },
  rail: { width: 14, alignItems: 'center', paddingTop: 18 },
  line: { position: 'absolute', top: 22, bottom: -2, width: 2, backgroundColor: colors.line, left: 6 },
  dot: { width: 10, height: 10, borderRadius: 5 },
  card: {
    flex: 1, backgroundColor: colors.bgNormal,
    borderRadius: radius.r14, padding: space.s14,
    borderWidth: 1, borderColor: colors.line, gap: space.s8,
  },
  cardSpaced: { marginBottom: space.s12 },
  cardLast: {},
  head: { flexDirection: 'row', alignItems: 'center', gap: space.s8 },
  headTextCol: { flex: 1 },
  actor: { fontSize: 13, color: colors.labelNormal },
  actorName: { fontWeight: '700' },
  actorLabel: { color: colors.labelAlternative },
  time: { fontSize: 12, fontWeight: '500', color: colors.labelAlternative },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: space.s6 },
  warnBadge: {
    width: 16, height: 16, borderRadius: 4,
    backgroundColor: colors.red95,
    alignItems: 'center', justifyContent: 'center',
  },
  title: {
    flex: 1, fontSize: 15, fontWeight: '700', color: colors.labelNormal,
    letterSpacing: -0.15, lineHeight: 21,
  },
});
