import React from 'react';
import { View, Text, StyleSheet, Pressable, ActivityIndicator } from 'react-native';
import { Feather } from '@expo/vector-icons';
import Avatar from '@/components/common/Avatar';
import { scale, colors, space, radius } from '@/styles/tokens';
import type { GroupMember } from '@/types/group';

interface Props {
  member: GroupMember;
  isFirst?: boolean;
  onPress?: (member: GroupMember) => void;
  onNudge?: (member: GroupMember) => void;
  nudging?: boolean;
}

// onNudge 가 없으면(호출자가 본인 행에는 넘기지 않음) 벨 영역 자체를 렌더하지 않는다 — 자기 자신 재촉 불가.
function MemberCard({ member, isFirst, onPress, onNudge, nudging = false }: Props) {
  return (
    <View style={[styles.row, !isFirst && styles.borderTop]}>
      <Pressable
        style={styles.content}
        onPress={() => onPress?.(member)}
        accessibilityLabel={`${member.name} ${member.role}`}
        accessibilityRole="button"
      >
        <View>
          <Avatar name={member.name[0]} tint={member.tint} size={scale(44)} />
          {member.online && <View style={styles.onlineDot} />}
        </View>
        <View style={styles.info}>
          <View style={styles.nameRow}>
            <Text style={styles.name}>{member.name}</Text>
            {member.isMe && <View style={styles.meBadge}><Text style={styles.meBadgeText}>나</Text></View>}
          </View>
        </View>
      </Pressable>
      {onNudge && (
        <Pressable
          style={styles.nudgeBtn}
          onPress={() => onNudge(member)}
          disabled={nudging}
          accessibilityLabel={`${member.name} 재촉하기`}
          accessibilityRole="button"
          hitSlop={8}
        >
          {nudging ? (
            <ActivityIndicator size="small" color={colors.labelAlternative} />
          ) : (
            <Feather name="bell" size={scale(18)} color={colors.labelAlternative} />
          )}
        </Pressable>
      )}
    </View>
  );
}

export default React.memo(MemberCard);

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
  },
  content: {
    flex: 1,
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
    padding: space.s14,
  },
  nudgeBtn: {
    width: scale(40), height: scale(40), marginRight: space.s14,
    alignItems: 'center', justifyContent: 'center',
  },
  borderTop: { borderTopWidth: 1, borderTopColor: colors.line },
  onlineDot: {
    position: 'absolute', right: -1, bottom: -1,
    width: scale(12), height: scale(12), borderRadius: scale(6),
    backgroundColor: colors.statusPositive, borderWidth: 2, borderColor: colors.staticWhite,
  },
  info: { flex: 1 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: space.s6 },
  name: { fontSize: scale(15), fontWeight: '700', color: colors.labelNormal, letterSpacing: -0.01 },
  meBadge: {
    paddingHorizontal: space.s6, paddingVertical: 2,
    backgroundColor: colors.fillStrong, borderRadius: radius.r4,
  },
  meBadgeText: { fontSize: scale(10), color: colors.labelAlternative, fontWeight: '600' },
});
