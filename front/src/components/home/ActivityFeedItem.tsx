import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { colors, typography, space, radius } from '@/styles/tokens';
import type { ActivityItem } from '@/store/slices/activityApi';

interface ActivityFeedItemProps {
  item: ActivityItem;
  onPress?: (item: ActivityItem) => void;
}

function formatRelativeTime(isoString: string): string {
  const diff = (Date.now() - new Date(isoString).getTime()) / 1000;
  if (diff < 60) return '방금 전';
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`;
  return `${Math.floor(diff / 86400)}일 전`;
}

function ActivityFeedItem({ item, onPress }: ActivityFeedItemProps) {
  return (
    <Pressable
      style={styles.container}
      onPress={() => onPress?.(item)}
      accessibilityLabel={`${item.actorName} ${item.action} ${formatRelativeTime(item.occurredAt)}`}
      accessibilityRole="button"
    >
      <View style={styles.avatar}>
        <Text style={styles.avatarEmoji}>{item.actorEmoji}</Text>
      </View>
      <View style={styles.content}>
        <View style={styles.row}>
          <Text style={styles.actor}>{item.actorName}</Text>
          <Text style={styles.time}>{formatRelativeTime(item.occurredAt)}</Text>
        </View>
        <Text style={styles.action}>{item.action}</Text>
        {item.detail ? (
          <Text style={styles.detail} numberOfLines={1}>{item.detail}</Text>
        ) : null}
      </View>
    </Pressable>
  );
}

export default React.memo(ActivityFeedItem);

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space.s12,
    paddingVertical: space.s12,
    paddingHorizontal: space.s16,
  },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: radius.full,
    backgroundColor: colors.bgAlt,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.line,
  },
  avatarEmoji: {
    fontSize: 18,
  },
  content: {
    flex: 1,
    gap: 2,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  actor: {
    ...typography.label1n,
    color: colors.labelNormal,
    fontWeight: '600',
  },
  time: {
    ...typography.caption1,
    color: colors.labelAssistive,
  },
  action: {
    ...typography.body2r,
    color: colors.labelNormal,
  },
  detail: {
    ...typography.caption1,
    color: colors.labelAlternative,
  },
});
