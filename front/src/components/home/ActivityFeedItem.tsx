import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import type { ActivityFeedItem as ActivityFeedItemType, ActivitySeverity } from '@/types/activity';

// Legacy export — 하위 호환 (사용처 없으면 Phase 2에서 제거)
export interface FeedActivity {
  id: number;
  who: string;
  tint: string;
  text: string;
  time: string;
}

interface Props {
  item: ActivityFeedItemType;
  onPress?: (item: ActivityFeedItemType) => void;
}

function severityTint(s: ActivitySeverity): string {
  return s === 'WARN' ? '#E02020' : colors.primaryNormal;
}

function formatTime(iso: string): string {
  const diffMin = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  if (diffMin < 1) return '방금';
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH}시간 전`;
  return `${Math.floor(diffH / 24)}일 전`;
}

function ActivityFeedItemComponent({ item, onPress }: Props) {
  const tint = severityTint(item.severity);
  return (
    <Pressable
      style={styles.container}
      onPress={() => onPress?.(item)}
      accessibilityLabel={`${item.actorNickname} ${item.summary} ${formatTime(item.occurredAt)}`}
      accessibilityRole="button"
    >
      <View style={[styles.avatar, { backgroundColor: tint }]}>
        <Text style={styles.avatarLetter}>{item.actorNickname.charAt(0)}</Text>
      </View>
      <View style={styles.content}>
        <Text style={styles.body}>
          {item.summary.startsWith(item.actorNickname) ? (
            <>
              <Text style={styles.nameSpan}>{item.actorNickname}</Text>
              {item.summary.slice(item.actorNickname.length)}
            </>
          ) : (
            item.summary
          )}
        </Text>
      </View>
      <Text style={styles.time}>{formatTime(item.occurredAt)}</Text>
    </Pressable>
  );
}

export default React.memo(ActivityFeedItemComponent);

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
    paddingVertical: space.s12, paddingHorizontal: space.s16,
  },
  avatar: { width: scale(36), height: scale(36), borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  avatarLetter: { fontSize: scale(14), fontWeight: '700', color: '#fff' },
  content: { flex: 1, gap: 4 },
  body: { ...typography.body2r, color: colors.labelNeutral },
  nameSpan: { fontWeight: '700', color: colors.labelNormal },
  time: { ...typography.caption1, color: colors.labelAlternative },
});
