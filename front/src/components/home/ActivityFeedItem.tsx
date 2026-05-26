import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { colors, typography, space, radius } from '@/styles/tokens';

export interface FeedActivity {
  id: number;
  who: string;
  tint: string;
  text: string;
  time: string;
}

interface ActivityFeedItemProps {
  item: FeedActivity;
  onPress?: (item: FeedActivity) => void;
}

function ActivityFeedItem({ item, onPress }: ActivityFeedItemProps) {
  return (
    <Pressable
      style={styles.container}
      onPress={() => onPress?.(item)}
      accessibilityLabel={`${item.who} ${item.text} ${item.time}`}
      accessibilityRole="button"
    >
      <View style={[styles.avatar, { backgroundColor: item.tint }]}>
        <Text style={styles.avatarLetter}>{item.who.charAt(0)}</Text>
      </View>
      <View style={styles.content}>
        <Text style={styles.body}>
          <Text style={styles.nameSpan}>{item.who}</Text>
          {'이(가) ' + item.text}
        </Text>
      </View>
      <Text style={styles.time}>{item.time}</Text>
    </Pressable>
  );
}

export default React.memo(ActivityFeedItem);

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.s12,
    paddingVertical: space.s12,
    paddingHorizontal: space.s16,
  },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarLetter: {
    fontSize: 14,
    fontWeight: '700',
    color: '#fff',
  },
  content: {
    flex: 1,
  },
  body: {
    ...typography.body2r,
    color: colors.labelNeutral,
  },
  nameSpan: {
    fontWeight: '700',
    color: colors.labelNormal,
  },
  time: {
    ...typography.caption1,
    color: colors.labelAlternative,
  },
});
