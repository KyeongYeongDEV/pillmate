import React, { useEffect } from 'react';
import { Pressable, View, Text, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import * as Notifications from 'expo-notifications';
import { useGetNotificationsQuery } from '@/store/slices/notificationApi';
import { unreadCount } from '@/lib/notificationMeta';
import { safePush } from '@/lib/router/safePush';
import { scale, colors, radius, space } from '@/styles/tokens';

const BADGE_MAX = 9;

export default function NotificationBell() {
  const { count } = useGetNotificationsQuery(undefined, {
    refetchOnFocus: true,
    refetchOnReconnect: true,
    selectFromResult: ({ data }) => ({ count: unreadCount(data ?? []) }),
  });

  // 인앱 읽음 상태(마크읽음/모두읽음)가 바뀌어도 OS 앱 아이콘 배지는 별개 상태라 저절로
  // 안 지워짐 — unreadCount 가 바뀔 때마다 OS 배지를 진실源(SENT 미읽음 개수)에 동기화한다.
  useEffect(() => {
    Notifications.setBadgeCountAsync(count).catch(() => {});
  }, [count]);

  return (
    <Pressable
      onPress={() => safePush('/notifications')}
      accessibilityLabel={count > 0 ? `알림 ${count}건` : '알림'}
      accessibilityRole="button"
      hitSlop={8}
    >
      <Feather name="bell" size={scale(22)} color={colors.labelNormal} />
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
    minWidth: scale(16), height: scale(16), paddingHorizontal: space.s4, borderRadius: radius.full,
    backgroundColor: colors.statusNegative, alignItems: 'center', justifyContent: 'center',
  },
  badgeText: { color: colors.staticWhite, fontSize: scale(10), fontWeight: '700' },
});
