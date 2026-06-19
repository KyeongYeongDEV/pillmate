import React from 'react';
import { Pressable, Text, StyleSheet, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { scale, colors, typography, space, radius } from '@/styles/tokens';

interface GroupSelectorProps {
  groupName: string;
  onPress?: () => void;
}

function GroupSelector({ groupName, onPress }: GroupSelectorProps) {
  return (
    <Pressable
      style={styles.container}
      onPress={onPress}
      accessibilityLabel={`현재 그룹: ${groupName}. 탭하여 변경`}
      accessibilityRole="button"
    >
      <Text style={styles.name} numberOfLines={1}>{groupName}</Text>
      <Feather name="chevron-down" size={scale(16)} color={colors.labelAlternative} />
    </Pressable>
  );
}

export default React.memo(GroupSelector);

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.s4,
    paddingHorizontal: space.s12,
    paddingVertical: space.s6,
    borderRadius: radius.full,
    backgroundColor: colors.bgAlt,
    borderWidth: 1,
    borderColor: colors.line,
    maxWidth: scale(200),
  },
  name: {
    ...typography.label1n,
    color: colors.labelNormal,
    fontWeight: '600',
  },
});
