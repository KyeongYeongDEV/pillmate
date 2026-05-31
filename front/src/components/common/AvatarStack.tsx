import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Avatar from '@/components/common/Avatar';
import { colors, radius } from '@/styles/tokens';

const OVERLAP = 18;
const MAX_VISIBLE = 3;

const TINTS = ['#FF7B2E', '#0066FF', '#6541F2', '#00BFA5', '#E91E63'];

interface AvatarStackProps {
  names: string[];
  size?: number;
}

function AvatarStack({ names, size = 36 }: AvatarStackProps) {
  const visible = names.slice(0, MAX_VISIBLE);
  const extra = names.length - MAX_VISIBLE;
  const totalWidth = visible.length * (size - OVERLAP) + OVERLAP + (extra > 0 ? size - OVERLAP : 0);

  return (
    <View style={[styles.container, { width: totalWidth, height: size }]}>
      {visible.map((name, i) => (
        <View key={i} style={[styles.avatarWrap, { left: i * (size - OVERLAP), zIndex: MAX_VISIBLE - i }]}>
          <Avatar name={name[0] ?? '?'} tint={TINTS[i % TINTS.length]} size={size} />
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
  moreText: { fontSize: 12, fontWeight: '700', color: colors.labelAlternative },
});
