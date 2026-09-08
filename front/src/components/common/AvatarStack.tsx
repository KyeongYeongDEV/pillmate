import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Avatar from '@/components/common/Avatar';
import { scale, colors, radius } from '@/styles/tokens';

const OVERLAP = 18;
const MAX_VISIBLE = 3;

const TINTS = [
  colors.patientOrange,
  colors.guardianBlue,
  colors.accentViolet,
  colors.accentTeal,
  colors.accentPink,
];

interface AvatarStackProps {
  names: string[];
  size?: number;
  // 구성원별 고유색(예: assignMemberColors 결과)을 names 와 같은 순서로 전달하면 그걸 쓴다.
  // 없으면 기존 역할 기반 고정 팔레트로 폴백(예: GroupCard 미리보기처럼 userId 를 모르는 호출부).
  tints?: string[];
}

function AvatarStack({ names, size = 36, tints }: AvatarStackProps) {
  const visible = names.slice(0, MAX_VISIBLE);
  const extra = names.length - MAX_VISIBLE;
  const totalWidth = visible.length * (size - OVERLAP) + OVERLAP + (extra > 0 ? size - OVERLAP : 0);

  return (
    <View style={[styles.container, { width: totalWidth, height: size }]}>
      {visible.map((name, i) => (
        <View key={i} style={[styles.avatarWrap, { left: i * (size - OVERLAP), zIndex: MAX_VISIBLE - i }]}>
          <Avatar name={name[0] ?? '?'} tint={tints?.[i] ?? TINTS[i % TINTS.length]} size={size} />
        </View>
      ))}
      {extra > 0 && (
        <View style={[styles.avatarWrap, styles.moreWrap, { left: visible.length * (size - OVERLAP), width: size, height: size, borderRadius: size / 2 }]}>
          <Text style={styles.moreText}>+{extra}</Text>
        </View>
      )}
    </View>
  );
}

export default React.memo(AvatarStack);

const styles = StyleSheet.create({
  container: { position: 'relative' },
  avatarWrap: { position: 'absolute', top: 0 },
  moreWrap: {
    backgroundColor: colors.fillStrong,
    borderWidth: 1.5,
    borderColor: colors.bgNormal,
    alignItems: 'center',
    justifyContent: 'center',
  },
  moreText: { fontSize: scale(12), fontWeight: '700', color: colors.labelAlternative },
});
