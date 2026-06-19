import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import AvatarStack from '@/components/common/AvatarStack';
import Avatar from '@/components/common/Avatar';
import { scale, colors, space, radius, typography, shadows } from '@/styles/tokens';
import { resolveEventStyle, isPersonalGroup, composeGroupDesc, getActivityLabel } from '@/lib/groupCardHelpers';
import type { MyGroupSummary } from '@/types/caregroup';

interface GroupCardProps {
  group: MyGroupSummary;
  onPress: (groupId: number) => void;
  onPinToggle: (groupId: number, pinned: boolean) => void;
  isPinned?: boolean;
}

const PERSONAL_AVATAR_TINT = colors.guardianBlue;

function GroupCard({ group, onPress, onPinToggle, isPinned }: GroupCardProps) {
  const personal = isPersonalGroup(group);
  const desc = composeGroupDesc(group);
  const eventStyle = resolveEventStyle(group.lastActivity?.activityType);
  const hasUnread = group.unreadCount > 0;

  return (
    <Pressable
      style={[
        styles.card,
        (isPinned || hasUnread) && styles.cardHighlight,
      ]}
      onPress={() => onPress(group.groupId)}
      accessibilityLabel={`${group.name} 그룹, ${group.memberCount}명`}
      accessibilityRole="button"
    >
      <View style={styles.avatarCol}>
        {personal
          ? <Avatar name={group.membersPreview[0]?.[0] ?? '나'} tint={PERSONAL_AVATAR_TINT} size={scale(44)} />
          : <AvatarStack names={group.membersPreview} size={scale(28)} />}
      </View>

      <View style={styles.contentCol}>
        <View style={styles.row1}>
          <Text style={styles.name} numberOfLines={1}>{group.name}</Text>
          {personal && (
            <View style={styles.privateBadge}>
              <Text style={styles.privateBadgeText}>비공개</Text>
            </View>
          )}
          {group.lastActivity && (
            <Text style={styles.time}>{formatRelativeTime(group.lastActivity.occurredAt)}</Text>
          )}
        </View>

        <Text style={styles.desc} numberOfLines={1}>{desc}</Text>

        {group.lastActivity && (
          <View style={styles.row3}>
            <View style={[styles.chip, { backgroundColor: eventStyle.bg }]}>
              <View style={[styles.chipDot, { backgroundColor: eventStyle.dot }]} />
              <Text style={[styles.chipText, { color: eventStyle.fg }]} numberOfLines={1}>
                {getActivityLabel(group.lastActivity.activityType)}
              </Text>
            </View>
            <Text style={styles.lastActivityText} numberOfLines={1}>
              {group.lastActivity.summary}
            </Text>
            {hasUnread && (
              <View style={styles.badge}>
                <Text style={styles.badgeText}>{group.unreadCount}</Text>
              </View>
            )}
          </View>
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
    borderRadius: radius.r14,
    padding: space.s14,
    borderWidth: 1,
    borderColor: colors.line,
    flexDirection: 'row',
    gap: space.s12,
    alignItems: 'flex-start',
    marginBottom: space.s8,
  },
  cardHighlight: {
    borderColor: colors.blue90,
    shadowColor: colors.primaryBase,
    shadowOffset: { width: 0, height: scale(4) },
    shadowOpacity: 0.06,
    shadowRadius: 12,
    elevation: 2,
  },
  avatarCol: { width: scale(52), height: scale(44), flexShrink: 0 },
  contentCol: { flex: 1, minWidth: 0 },
  row1: { flexDirection: 'row', alignItems: 'center', gap: space.s6 },
  name: {
    fontSize: scale(15), fontWeight: '700', color: colors.labelNormal, letterSpacing: -0.18,
    flexShrink: 1,
  },
  privateBadge: {
    paddingHorizontal: 6, paddingVertical: 1,
    borderRadius: radius.r4, backgroundColor: colors.fillNormal,
  },
  privateBadgeText: { fontSize: scale(10), fontWeight: '700', color: colors.labelAlternative },
  time: { fontSize: scale(12), color: colors.labelAlternative, fontWeight: '500', marginLeft: 'auto' },
  desc: { fontSize: scale(12), color: colors.labelAlternative, marginTop: 2 },
  row3: { flexDirection: 'row', alignItems: 'center', gap: space.s6, marginTop: space.s8 },
  chip: {
    flexDirection: 'row', alignItems: 'center', gap: space.s4,
    paddingHorizontal: space.s8, paddingVertical: 3,
    borderRadius: radius.full, flexShrink: 0,
  },
  chipDot: { width: scale(4), height: scale(4), borderRadius: scale(2) },
  chipText: { fontSize: scale(11), fontWeight: '600' },
  lastActivityText: { flex: 1, fontSize: scale(13), fontWeight: '500', color: colors.labelNeutral },
  badge: {
    minWidth: scale(18), height: scale(18), borderRadius: scale(9),
    backgroundColor: colors.primaryBase,
    alignItems: 'center', justifyContent: 'center',
    paddingHorizontal: 5,
  },
  badgeText: { fontSize: scale(11), fontWeight: '700', color: colors.staticWhite },
});
