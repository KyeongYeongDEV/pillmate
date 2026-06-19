import React, { useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { scale, colors, typography, space, radius, shadows } from '@/styles/tokens';
import type { TodayProgress } from '@/store/slices/activityApi';

interface TodayProgressCardProps {
  progress: TodayProgress;
}

function TodayProgressCard({ progress }: TodayProgressCardProps) {
  const percent = useMemo(() => {
    if (progress.total === 0) return 0;
    return Math.round((progress.taken / progress.total) * 100);
  }, [progress.taken, progress.total]);

  return (
    <View style={styles.card}>
      <View style={styles.row}>
        <Text style={styles.title}>오늘의 복약</Text>
        <View style={styles.countBadge}>
          <Text style={styles.countText}>
            {progress.taken}/{progress.total}
          </Text>
          <Text style={styles.percentText}>{percent}%</Text>
        </View>
      </View>

      {/* Progress bar */}
      <View style={styles.barBg}>
        <View style={[styles.barFill, { width: `${percent}%` }]} />
      </View>

      {progress.nextScheduleTime && (
        <Text style={styles.next}>
          다음: {progress.nextScheduleLabel ?? '다음 복약'} ({progress.nextScheduleTime})
        </Text>
      )}
      {progress.total > 0 && progress.taken === progress.total && (
        <Text style={styles.done}>오늘 복약을 모두 완료했습니다</Text>
      )}
    </View>
  );
}

export default React.memo(TodayProgressCard);

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16,
    padding: space.s16,
    gap: space.s12,
    borderWidth: 1,
    borderColor: colors.line,
    ...shadows.small,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    ...typography.headline2,
    color: colors.labelNormal,
  },
  countBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.s6,
  },
  countText: {
    ...typography.body1n,
    color: colors.labelNeutral,
    fontWeight: '600',
  },
  percentText: {
    ...typography.headline2,
    color: colors.primaryNormal,
    fontWeight: '700',
  },
  barBg: {
    width: '100%',
    height: scale(8),
    backgroundColor: colors.bgAlt,
    borderRadius: radius.full,
    overflow: 'hidden',
  },
  barFill: {
    height: scale(8),
    backgroundColor: colors.primaryNormal,
    borderRadius: radius.full,
  },
  next: {
    ...typography.body2r,
    color: colors.labelAlternative,
  },
  done: {
    ...typography.body2r,
    color: colors.statusPositive,
    fontWeight: '600',
  },
});
