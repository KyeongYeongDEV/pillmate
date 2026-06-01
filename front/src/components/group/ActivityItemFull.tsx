import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import Avatar from '@/components/common/Avatar';
import { colors, space, radius } from '@/styles/tokens';
import type { ActivityView } from '@/types/caregroup';

const DOT_COLOR: Record<string, string> = {
  DOSE_TAKEN: colors.statusPositive,
  DOSE_MISSED: colors.statusNegative,
  AI_INSIGHT: colors.violet45,
  AI_REPORT: colors.violet45,
  PRESCRIPTION_ADDED: colors.primaryBase,
  COMMENT: colors.cyan50,
  MEMBER_JOINED: colors.pink46,
};

const DEFAULT_DOT = colors.labelAlternative;

interface ActivityItemFullProps {
  item: ActivityView;
  last?: boolean;
}

function ActivityItemFull({ item, last }: ActivityItemFullProps) {
  const dotColor = DOT_COLOR[item.activityType] ?? DEFAULT_DOT;
  const isMiss = item.activityType === 'DOSE_MISSED';
  const time = formatTime(item.occurredAt);

  return (
    <View style={styles.wrapper}>
      <View style={styles.rail}>
        <View style={[styles.line, last && styles.lineHalf]} />
        <View style={[styles.dot, { backgroundColor: dotColor }]} />
      </View>

      <View style={[styles.cardWrap, !last && styles.cardWrapSpaced]}>
        <View style={styles.card}>
          <View style={styles.head}>
            <Avatar name={item.actorName[0] ?? '?'} tint={dotColor} size={28} />
            <View style={styles.headTextCol}>
              <Text style={styles.actorName}>{item.actorName}</Text>
            </View>
            <Text style={styles.time}>{time}</Text>
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
    </View>
  );
}

function formatTime(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const isToday = date.toDateString() === now.toDateString();
  if (isToday) {
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  }
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) {
    return '어제';
  }
  return `${date.getMonth() + 1}.${date.getDate()}`;
}

export default React.memo(ActivityItemFull);

const styles = StyleSheet.create({
  wrapper: { flexDirection: 'row', gap: space.s14 },
  rail: { width: 14, position: 'relative', flexShrink: 0 },
  line: {
    position: 'absolute', left: '50%', top: 22, bottom: -2,
    width: 2, backgroundColor: colors.line, marginLeft: -1,
  },
  lineHalf: { bottom: '50%' },
  dot: {
    position: 'absolute', left: '50%', top: 18, width: 10, height: 10,
    borderRadius: 5, marginLeft: -5,
    shadowColor: colors.bgAlt, shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 1, shadowRadius: 3,
  },
  cardWrap: { flex: 1, minWidth: 0 },
  cardWrapSpaced: { paddingBottom: space.s12 },
  card: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r14,
    padding: space.s14,
    paddingHorizontal: space.s16,
    borderWidth: 1, borderColor: colors.line,
  },
  head: { flexDirection: 'row', alignItems: 'center', gap: space.s8 },
  headTextCol: { flex: 1 },
  actorName: { fontSize: 13, fontWeight: '700', color: colors.labelNormal },
  time: { fontSize: 12, color: colors.labelAlternative, fontWeight: '500' },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: space.s6, marginTop: space.s10 },
  warnBadge: {
    width: 16, height: 16, borderRadius: 4,
    backgroundColor: colors.red95,
    alignItems: 'center', justifyContent: 'center',
  },
  title: {
    flex: 1, fontSize: 15, fontWeight: '700',
    color: colors.labelNormal, letterSpacing: -0.15, lineHeight: 21,
  },
});
