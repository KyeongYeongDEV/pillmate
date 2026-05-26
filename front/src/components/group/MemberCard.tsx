import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import Avatar from '@/components/common/Avatar';
import { colors, space, radius } from '@/styles/tokens';
import type { GroupMember } from '@/types/group';

interface Props {
  member: GroupMember;
  isFirst?: boolean;
  onPress?: (member: GroupMember) => void;
}

function MemberCard({ member, isFirst, onPress }: Props) {
  const isPatient = member.role === '환자';
  return (
    <Pressable
      style={[styles.row, !isFirst && styles.borderTop]}
      onPress={() => onPress?.(member)}
      accessibilityLabel={`${member.name} ${member.role}`}
      accessibilityRole="button"
    >
      <View>
        <Avatar name={member.name[0]} tint={member.tint} size={44} />
        {member.online && <View style={styles.onlineDot} />}
      </View>
      <View style={styles.info}>
        <View style={styles.nameRow}>
          <Text style={styles.name}>{member.name}</Text>
          {member.isMe && <View style={styles.meBadge}><Text style={styles.meBadgeText}>나</Text></View>}
        </View>
        <Text style={styles.sub}>{member.sub}</Text>
      </View>
      <View style={[styles.roleBadge, isPatient ? styles.patientBadge : styles.guardianBadge]}>
        <Text style={[styles.roleText, isPatient ? styles.patientText : styles.guardianText]}>
          {member.role}
        </Text>
      </View>
    </Pressable>
  );
}

export default React.memo(MemberCard);

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
    padding: space.s14,
  },
  borderTop: { borderTopWidth: 1, borderTopColor: colors.line },
  onlineDot: {
    position: 'absolute', right: -1, bottom: -1,
    width: 12, height: 12, borderRadius: 6,
    backgroundColor: colors.statusPositive, borderWidth: 2, borderColor: '#fff',
  },
  info: { flex: 1 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: space.s6 },
  name: { fontSize: 15, fontWeight: '700', color: colors.labelNormal, letterSpacing: -0.01 },
  meBadge: {
    paddingHorizontal: space.s6, paddingVertical: 2,
    backgroundColor: colors.fillStrong, borderRadius: radius.r4,
  },
  meBadgeText: { fontSize: 10, color: colors.labelAlternative, fontWeight: '600' },
  sub: { fontSize: 12, color: colors.labelAlternative, marginTop: 1 },
  roleBadge: { paddingHorizontal: space.s10, paddingVertical: 4, borderRadius: radius.r6 },
  patientBadge: { backgroundColor: colors.orange95 },
  guardianBadge: { backgroundColor: colors.blue95 },
  roleText: { fontSize: 11, fontWeight: '600' },
  patientText: { color: colors.orange40 },
  guardianText: { color: colors.primaryNormal },
});
