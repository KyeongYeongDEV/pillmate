import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { colors, typography, space, radius } from '@/styles/tokens';
import type { ActivityFeedItem as ApiActivityFeedItem, ActivitySeverity } from '@/types/activity';

// Legacy shape (home mock → real API bridge)
export interface FeedActivity {
  id: number;
  who: string;
  tint: string;
  text: string;
  time: string;
}

interface ActivityFeedItemProps {
  item: ApiActivityFeedItem;
  onPress?: (item: ApiActivityFeedItem) => void;
}

function severityTint(severity: ActivitySeverity): string {
  if (severity === 'CRITICAL') return '#E02020';
  if (severity === 'WARN') return '#F5A623';
  return colors.primaryNormal;
}

function severityBg(severity: ActivitySeverity): string {
  if (severity === 'CRITICAL') return '#FFF0F0';
  if (severity === 'WARN') return '#FFFBF0';
  return colors.blue95;
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  const diffMs = Date.now() - d.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return '방금';
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH}시간 전`;
  return `${Math.floor(diffH / 24)}일 전`;
}

function ActivityFeedItemComponent({ item, onPress }: ActivityFeedItemProps) {
  const tint = severityTint(item.severity);
  const bg = severityBg(item.severity);
  return (
    <Pressable
      style={styles.container}
      onPress={() => onPress?.(item)}
      accessibilityLabel={`${item.actorName} ${item.summary} ${formatTime(item.occurredAt)}`}
      accessibilityRole="button"
    >
      <View style={[styles.avatar, { backgroundColor: tint }]}>
        <Text style={styles.avatarLetter}>{item.actorName.charAt(0)}</Text>
      </View>
      <View style={styles.content}>
        <Text style={styles.body}>
          <Text style={styles.nameSpan}>{item.actorName}</Text>
          {'이(가) ' + item.summary}
        </Text>
        {item.severity !== 'INFO' && (
          <View style={[styles.severityBadge, { backgroundColor: bg }]}>
            <Text style={[styles.severityText, { color: tint }]}>
              {item.severity === 'CRITICAL' ? '⚠ 위험' : '⚠ 주의'}
            </Text>
          </View>
        )}
      </View>
      <Text style={styles.time}>{formatTime(item.occurredAt)}</Text>
    </Pressable>
  );
}

export default React.memo(ActivityFeedItemComponent);

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.s12,
    paddingVertical: space.s12,
    paddingHorizontal: space.s16,
  },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarLetter: { fontSize: 14, fontWeight: '700', color: '#fff' },
  content: { flex: 1, gap: 4 },
  body: { ...typography.body2r, color: colors.labelNeutral },
  nameSpan: { fontWeight: '700', color: colors.labelNormal },
  severityBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: space.s6,
    paddingVertical: 2,
    borderRadius: radius.r4,
  },
  severityText: { fontSize: 11, fontWeight: '700' },
  time: { ...typography.caption1, color: colors.labelAlternative },
});
