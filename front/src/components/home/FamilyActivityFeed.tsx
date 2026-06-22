import React from 'react';
import { View, Text, StyleSheet, Pressable, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { colors, space, radius, shadows, typography, scale } from '@/styles/tokens';
import ActivityFeedItem from '@/components/home/ActivityFeedItem';
import type { ActivityFeedItem as ActivityFeedItemType } from '@/types/activity';

interface Props {
  feed: ActivityFeedItemType[];
  isLoading: boolean;
  isError: boolean;
  hasPinnedGroup: boolean;
}

export default function FamilyActivityFeed({ feed, isLoading, isError, hasPinnedGroup }: Props) {
  if (!hasPinnedGroup) {
    return (
      <View style={styles.noPinCard} testID="no-pinned-group">
        <Text style={styles.noPinMsg}>그룹을 고정하면 여기서 활동을 모아볼 수 있어요</Text>
        <Pressable
          onPress={() => router.push('/(tabs)/group' as any)}
          accessibilityLabel="그룹 탭으로 이동"
          accessibilityRole="button"
        >
          <Text style={styles.noPinCta}>그룹 고정하러 가기 →</Text>
        </Pressable>
      </View>
    );
  }
  if (isLoading) {
    return (
      <View style={styles.placeholder} testID="activity-loading">
        <ActivityIndicator size="small" color={colors.primaryBase} />
        <Text style={styles.placeholderTxt}>활동 불러오는 중…</Text>
      </View>
    );
  }
  if (isError) {
    return (
      <View style={styles.placeholder} testID="activity-error">
        <Text style={styles.placeholderTxt}>활동을 불러올 수 없어요</Text>
      </View>
    );
  }
  if (feed.length === 0) {
    return (
      <View style={styles.placeholder} testID="activity-empty">
        <Text style={styles.placeholderTxt}>아직 가족 활동이 없어요</Text>
      </View>
    );
  }
  return (
    <View style={styles.feedCard} testID="activity-data">
      {feed.map((item, idx) => (
        <React.Fragment key={`${item.actorNickname}-${item.occurredAt}`}>
          <ActivityFeedItem item={item} />
          {idx < feed.length - 1 && <View style={styles.separator} />}
        </React.Fragment>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  noPinCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, padding: space.s20,
    alignItems: 'center', gap: space.s10,
  },
  noPinMsg: { ...typography.body2r, color: colors.labelAlternative, textAlign: 'center' },
  noPinCta: { ...typography.label1n, color: colors.primaryNormal, fontWeight: '600' },
  placeholder: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, padding: space.s20,
    alignItems: 'center', gap: space.s8,
  },
  placeholderTxt: { ...typography.body2r, color: colors.labelAlternative },
  feedCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, overflow: 'hidden', ...shadows.small,
  },
  separator: { height: scale(1), backgroundColor: colors.line, marginLeft: 64 },
});
