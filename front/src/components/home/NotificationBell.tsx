import React from 'react';
import { Pressable, View, Text, StyleSheet } from 'react-native';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { useGetNotificationsQuery } from '@/store/slices/notificationApi';
import { unreadCount } from '@/lib/notificationMeta';
import { colors, radius, space } from '@/styles/tokens';

const BADGE_MAX = 9;

export default function NotificationBell() {
  const { data = [] } = useGetNotificationsQuery();
  const count = unreadCount(data);
  return (
    <Pressable
      onPress={() => router.push('/notifications' as any)}
      accessibilityLabel={count > 0 ? `알림 ${count}건` : '알림'}
      accessibilityRole="button"
      hitSlop={8}
    >
      <Feather name="bell" size={22} color={colors.labelNormal} />
      {count > 0 && (
        <View style={styles.badge}>
          <Text style={styles.badgeText}>{count > BADGE_MAX ? `${BADGE_MAX}+` : count}</Text>
        </View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  badge: {
    position: 'absolute', top: -space.s4, right: -space.s4,
    minWidth: 16, height: 16, paddingHorizontal: space.s4, borderRadius: radius.full,
    backgroundColor: colors.statusNegative, alignItems: 'center', justifyContent: 'center',
  },
  badgeText: { color: colors.staticWhite, fontSize: 10, fontWeight: '700' },
});
