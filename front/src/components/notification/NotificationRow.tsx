import React, { memo } from 'react';
import { Pressable, View, Text, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import type { NotificationItem } from '@/types/notification';
import { notificationMeta } from '@/lib/notificationMeta';
import { relativeTime } from '@/utils/relativeTime';
import { scale, colors, space, radius } from '@/styles/tokens';

interface Props {
  item: NotificationItem;
  onPress: (item: NotificationItem) => void;
}

function NotificationRow({ item, onPress }: Props) {
  const { icon, color } = notificationMeta(item.type);
  const unread = item.status !== 'READ';
  return (
    <Pressable
      style={[styles.row, unread && styles.rowUnread]}
      onPress={() => onPress(item)}
      accessibilityRole="button"
      accessibilityLabel={`${item.title}${unread ? ', 안 읽음' : ''}`}
    >
      <View style={[styles.iconWrap, { backgroundColor: `${color}1A` }]}>
        <Feather name={icon} size={scale(18)} color={color} />
      </View>
      <View style={styles.body}>
        <View style={styles.topRow}>
          <Text style={[styles.title, unread && styles.titleUnread]} numberOfLines={1}>{item.title}</Text>
          <Text style={styles.time}>{relativeTime(item.createdAt)}</Text>
        </View>
        <Text style={styles.bodyText} numberOfLines={2}>{item.body}</Text>
      </View>
      {unread && <View style={styles.unreadDot} />}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row', alignItems: 'flex-start', gap: space.s12,
    paddingHorizontal: space.s16, paddingVertical: space.s14,
    backgroundColor: colors.bgNormal,
  },
  rowUnread: { backgroundColor: colors.blue95 },
  iconWrap: { width: scale(36), height: scale(36), borderRadius: radius.full, alignItems: 'center', justifyContent: 'center' },
  body: { flex: 1, gap: space.s4 },
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space.s8 },
  title: { flex: 1, fontSize: scale(15), fontWeight: '600', color: colors.labelNormal },
  titleUnread: { fontWeight: '700' },
  time: { fontSize: scale(12), color: colors.labelAssistive },
  bodyText: { fontSize: scale(13), color: colors.labelAlternative, lineHeight: scale(18) },
  unreadDot: { width: scale(8), height: scale(8), borderRadius: radius.full, backgroundColor: colors.primaryBase, marginTop: space.s4 },
});

export default memo(NotificationRow);
