import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Feather } from '@expo/vector-icons';
import AvatarStack from '@/components/common/AvatarStack';
import { colors, space, radius, typography, shadows } from '@/styles/tokens';
import type { MyGroupSummary } from '@/types/caregroup';

interface GroupCardProps {
  group: MyGroupSummary;
  onPress: (groupId: number) => void;
  onPinToggle: (groupId: number, pinned: boolean) => void;
  isPinned?: boolean;
}

function GroupCard({ group, onPress, onPinToggle, isPinned }: GroupCardProps) {
  return (
    <Pressable
      style={[styles.card, isPinned && styles.pinnedCard]}
      onPress={() => onPress(group.groupId)}
      accessibilityLabel={`${group.name} 그룹, ${group.memberCount}명`}
      accessibilityRole="button"
    >
      <View style={styles.topRow}>
        <AvatarStack names={group.membersPreview} size={36} />
        <View style={styles.info}>
          <Text style={styles.name} numberOfLines={1}>{group.name}</Text>
          <Text style={styles.sub}>{group.memberCount}명 · {group.role}</Text>
        </View>
        <Pressable
          onPress={() => onPinToggle(group.groupId, group.pinned)}
          accessibilityLabel={group.pinned ? '핀 해제' : '고정하기'}
          accessibilityRole="button"
          hitSlop={8}
        >
          <Feather
            name="bookmark"
            size={18}
            color={group.pinned ? colors.primaryBase : colors.labelAssistive}
          />
        </Pressable>
      </View>

      {group.lastActivity && (
        <View style={styles.lastActivity}>
          <Text style={styles.lastActivityText} numberOfLines={1}>
            {group.lastActivity.summary}
          </Text>
        </View>
      )}

      <View style={styles.bottomRow}>
        {group.unreadCount > 0 && (
          <View style={styles.badge}>
            <Text style={styles.badgeText}>{group.unreadCount}</Text>
          </View>
        )}
        {group.lastActivity && (
          <Text style={styles.time}>{formatRelativeTime(group.lastActivity.occurredAt)}</Text>
        )}
      </View>
    </Pressable>
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

export default React.memo(GroupCard);

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16,
    padding: space.s16,
    borderWidth: 1,
    borderColor: colors.line,
    gap: space.s10,
    ...shadows.small,
  },
  pinnedCard: {
    borderColor: colors.primaryBase,
    backgroundColor: '#F0F6FF',
  },
  topRow: { flexDirection: 'row', alignItems: 'center', gap: space.s12 },
  info: { flex: 1 },
  name: { ...typography.headline1, color: colors.labelNormal, letterSpacing: -0.01 },
  sub: { fontSize: 12, color: colors.labelAlternative, marginTop: 2 },
  lastActivity: {
    backgroundColor: colors.bgAlt,
    borderRadius: radius.r8,
    paddingHorizontal: space.s10,
    paddingVertical: space.s6,
  },
  lastActivityText: { fontSize: 13, color: colors.labelAlternative, lineHeight: 18 },
  bottomRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: space.s8 },
  badge: {
    minWidth: 18, height: 18, borderRadius: 9,
    backgroundColor: colors.statusNegative,
    alignItems: 'center', justifyContent: 'center',
    paddingHorizontal: 5,
  },
  badgeText: { fontSize: 11, fontWeight: '700', color: '#fff' },
  time: { fontSize: 11, color: colors.labelAlternative },
});
