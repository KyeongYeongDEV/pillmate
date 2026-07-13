import React from 'react';
import { View, StyleSheet } from 'react-native';
import { colors, space, radius } from '@/styles/tokens';

// redux-persist rehydrate 대기 / 날짜 폴백 시 빈 화면 대신 표시하는 최소 스켈레톤.
export default function BootSkeleton() {
  return (
    <View style={styles.root}>
      <View style={styles.block} />
      <View style={[styles.block, styles.tall]} />
      <View style={styles.block} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bgNormal, padding: space.s16, gap: space.s16, justifyContent: 'center' },
  block: { height: 64, borderRadius: radius.r16, backgroundColor: colors.fillNormal },
  tall: { height: 220 },
});
