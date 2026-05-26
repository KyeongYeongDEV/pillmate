import React from 'react';
import { Pressable, Text, StyleSheet, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { colors, typography, space, radius } from '@/styles/tokens';

interface NotificationBellProps {
  count: number;
  onPress?: () => void;
}

function NotificationBell({ count, onPress }: NotificationBellProps) {
  return (
    <Pressable
      style={styles.container}
      onPress={onPress}
      accessibilityLabel={count > 0 ? `알림 ${count}개 안 읽음` : '알림'}
      accessibilityRole="button"
    >
      <Feather name="bell" size={22} color={colors.labelNormal} />
      {count > 0 && (
        <View style={styles.badge}>
          <Text style={styles.badgeText}>{count > 99 ? '99+' : count}</Text>
        </View>
      )}
    </Pressable>
  );
}

export default React.memo(NotificationBell);

const styles = StyleSheet.create({
  container: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badge: {
    position: 'absolute',
    top: 4,
    right: 4,
    minWidth: 16,
    height: 16,
    borderRadius: radius.full,
    backgroundColor: colors.statusNegative,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 3,
  },
  badgeText: {
    fontSize: 9,
    color: '#fff',
    fontWeight: '700',
  },
});
