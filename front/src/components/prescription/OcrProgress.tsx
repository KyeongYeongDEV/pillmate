import React from 'react';
import { View, Text, Pressable, ActivityIndicator, StyleSheet } from 'react-native';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import { useOcrProgress } from '@/hooks/useOcrProgress';

export type OcrPhase = 'progressing' | 'failed';

type DisplayStatus = 'done' | 'active' | 'pending' | 'failed';

const STAGE_ICON: Record<'done' | 'pending' | 'failed', string> = {
  done: '✓',
  pending: '○',
  failed: '✗',
};

// failed phase 단계: 업로드 완료 → AI 인식 실패 → 매칭 비활성
const FAILED_STAGES: { key: string; label: string; status: DisplayStatus }[] = [
  { key: 'upload', label: '이미지 업로드', status: 'done' },
  { key: 'recognize', label: 'AI 약 인식 실패', status: 'failed' },
  { key: 'match', label: '약 정보 매칭', status: 'pending' },
];

interface Props {
  onRetry: () => void;
  onBack?: () => void;
  phase?: OcrPhase;
}

function OcrProgress({ onRetry, onBack, phase = 'progressing' }: Props) {
  const failed = phase === 'failed';
  const live = useOcrProgress(!failed); // 타이머는 진행 중일 때만

  const title = failed
    ? '약 인식에 실패했어요'
    : live.isSlow ? '오래 걸리네요, 잠시만 더…' : 'AI가 약을 분석하고 있어요';
  const subtitle = failed ? '잠시 후 다시 시도해 주세요' : `${live.elapsedSec}초 경과`;
  const barWidth = failed ? 100 : Math.round(live.progress * 100);
  const stages: { key: string; label: string; status: DisplayStatus }[] =
    failed ? FAILED_STAGES : live.stages;

  return (
    <View style={styles.root}>
      <Text style={[styles.title, failed && styles.titleFailed]}>{title}</Text>
      <Text style={styles.subtitle}>{subtitle}</Text>

      <View style={styles.barTrack} accessibilityLabel={failed ? '인식 실패' : `진행률 ${barWidth}%`}>
        <View style={[styles.barFill, failed && styles.barFillFailed, { width: `${barWidth}%` }]} />
      </View>

      <View style={styles.stages}>
        {stages.map(stage => (
          <StageRow key={stage.key} stage={stage} />
        ))}
      </View>

      {failed ? (
        <View style={styles.actions}>
          <Pressable style={styles.primaryBtn} onPress={onRetry} accessibilityLabel="다시 시도" accessibilityRole="button">
            <Text style={styles.primaryTxt}>다시 시도</Text>
          </Pressable>
          {onBack && (
            <Pressable style={styles.secondaryBtn} onPress={onBack} accessibilityLabel="뒤로" accessibilityRole="button">
              <Text style={styles.secondaryTxt}>뒤로</Text>
            </Pressable>
          )}
        </View>
      ) : (
        live.canRetry && (
          <Pressable style={styles.primaryBtn} onPress={onRetry} accessibilityLabel="다시 시도" accessibilityRole="button">
            <Text style={styles.primaryTxt}>다시 시도</Text>
          </Pressable>
        )
      )}
    </View>
  );
}

function StageRow({ stage }: { stage: { key: string; label: string; status: DisplayStatus } }) {
  const { status, label } = stage;
  return (
    <View style={styles.stageRow}>
      <View style={styles.stageIcon}>
        {status === 'active' ? (
          <ActivityIndicator size="small" color={colors.primaryNormal} />
        ) : (
          <Text
            style={[
              styles.stageIconTxt,
              status === 'done' && styles.stageIconDone,
              status === 'failed' && styles.stageIconFailed,
            ]}
          >
            {STAGE_ICON[status]}
          </Text>
        )}
      </View>
      <Text
        style={[
          styles.stageLabel,
          status === 'active' && styles.stageLabelActive,
          status === 'pending' && styles.stageLabelPending,
          status === 'failed' && styles.stageLabelFailed,
        ]}
      >
        {label}{status === 'active' ? ' 중…' : ''}
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
  titleFailed: { color: colors.statusNegative },
  subtitle: { ...typography.body2r, color: colors.labelAlternative },
  barTrack: {
    width: '100%', height: scale(8), borderRadius: radius.r12,
    backgroundColor: colors.fillNormal, overflow: 'hidden',
  },
  barFill: { height: '100%', borderRadius: radius.r12, backgroundColor: colors.primaryNormal },
  barFillFailed: { backgroundColor: colors.fillStrong },
  stages: { width: '100%', gap: space.s12, marginTop: space.s8 },
  stageRow: { flexDirection: 'row', alignItems: 'center', gap: space.s12 },
  stageIcon: { width: scale(24), alignItems: 'center', justifyContent: 'center' },
  stageIconTxt: { ...typography.body1n, color: colors.labelAssistive },
  stageIconDone: { color: colors.primaryNormal },
  stageIconFailed: { color: colors.statusNegative, fontWeight: '700' },
  stageLabel: { ...typography.body1n, color: colors.labelNeutral },
  stageLabelActive: { color: colors.labelNormal, fontWeight: '700' },
  stageLabelPending: { color: colors.labelAssistive },
  stageLabelFailed: { color: colors.statusNegative, fontWeight: '700' },
  actions: { flexDirection: 'row', gap: space.s12, marginTop: space.s8 },
  primaryBtn: {
    marginTop: space.s8, paddingHorizontal: space.s24, paddingVertical: space.s12,
    borderRadius: radius.r12, backgroundColor: colors.primaryNormal,
  },
  primaryTxt: { ...typography.body2n, color: colors.staticWhite, fontWeight: '700' },
  secondaryBtn: {
    marginTop: space.s8, paddingHorizontal: space.s24, paddingVertical: space.s12,
    borderRadius: radius.r12, backgroundColor: colors.fillNormal,
  },
  secondaryTxt: { ...typography.body2n, color: colors.labelNeutral, fontWeight: '600' },
});
