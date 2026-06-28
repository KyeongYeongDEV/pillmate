import React from 'react';
import { View, Text, Pressable, ActivityIndicator, StyleSheet } from 'react-native';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import { useOcrProgress, type OcrStage } from '@/hooks/useOcrProgress';

interface Props {
  onRetry: () => void;
}

function OcrProgress({ onRetry }: Props) {
  const { elapsedSec, progress, stages, isSlow, canRetry } = useOcrProgress(true);
  const message = isSlow ? '오래 걸리네요, 잠시만 더…' : 'AI가 약을 분석하고 있어요';

  return (
    <View style={styles.root}>
      <Text style={styles.title}>{message}</Text>
      <Text style={styles.elapsed}>{elapsedSec}초 경과</Text>

      <View style={styles.barTrack} accessibilityLabel={`진행률 ${Math.round(progress * 100)}%`}>
        <View style={[styles.barFill, { width: `${Math.round(progress * 100)}%` }]} />
      </View>

      <View style={styles.stages}>
        {stages.map(stage => (
          <StageRow key={stage.key} stage={stage} />
        ))}
      </View>

      {canRetry && (
        <Pressable
          style={styles.retryBtn}
          onPress={onRetry}
          accessibilityLabel="다시 시도"
          accessibilityRole="button"
        >
          <Text style={styles.retryTxt}>다시 시도</Text>
        </Pressable>
      )}
    </View>
  );
}

function StageRow({ stage }: { stage: OcrStage }) {
  const isActive = stage.status === 'active';
  const isDone = stage.status === 'done';
  return (
    <View style={styles.stageRow}>
      <View style={styles.stageIcon}>
        {isActive ? (
          <ActivityIndicator size="small" color={colors.primaryNormal} />
        ) : (
          <Text style={[styles.stageIconTxt, isDone && styles.stageIconDone]}>
            {isDone ? '✓' : '○'}
          </Text>
        )}
      </View>
      <Text
        style={[
          styles.stageLabel,
          isActive && styles.stageLabelActive,
          stage.status === 'pending' && styles.stageLabelPending,
        ]}
      >
        {stage.label}{isActive ? ' 중…' : ''}
      </Text>
    </View>
  );
}

export default React.memo(OcrProgress);

const styles = StyleSheet.create({
  root: {
    flex: 1, backgroundColor: colors.bgAlt,
    alignItems: 'center', justifyContent: 'center',
    paddingHorizontal: space.s32, gap: space.s16,
  },
  title: { ...typography.headline2, color: colors.labelNormal, textAlign: 'center' },
  elapsed: { ...typography.body2r, color: colors.labelAlternative },
  barTrack: {
    width: '100%', height: scale(8), borderRadius: radius.r12,
    backgroundColor: colors.fillNormal, overflow: 'hidden',
  },
  barFill: { height: '100%', borderRadius: radius.r12, backgroundColor: colors.primaryNormal },
  stages: { width: '100%', gap: space.s12, marginTop: space.s8 },
  stageRow: { flexDirection: 'row', alignItems: 'center', gap: space.s12 },
  stageIcon: { width: scale(24), alignItems: 'center', justifyContent: 'center' },
  stageIconTxt: { ...typography.body1n, color: colors.labelAssistive },
  stageIconDone: { color: colors.primaryNormal },
  stageLabel: { ...typography.body1n, color: colors.labelNeutral },
  stageLabelActive: { color: colors.labelNormal, fontWeight: '700' },
  stageLabelPending: { color: colors.labelAssistive },
  retryBtn: {
    marginTop: space.s8, paddingHorizontal: space.s24, paddingVertical: space.s12,
    borderRadius: radius.r12, backgroundColor: colors.primaryNormal,
  },
  retryTxt: { ...typography.body2n, color: colors.staticWhite, fontWeight: '700' },
});
