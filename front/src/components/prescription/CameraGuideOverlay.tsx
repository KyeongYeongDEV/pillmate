import React, { memo, useEffect, useRef } from 'react';
import { View, Text, StyleSheet, Animated } from 'react-native';
import { scale, space, radius } from '@/styles/tokens';
import type { CameraHints, HintStatus } from '@/hooks/useCameraGuide';

const HINT_META: Record<string, { icon: string; label: string }> = {
  stability: { icon: '📷', label: '흔들림' },
  brightness: { icon: '💡', label: '조명' },
  tilt:       { icon: '📐', label: '각도' },
};

const STATUS_COLOR: Record<HintStatus, string> = {
  ok:      'rgba(0, 191, 64, 0.9)',
  warn:    'rgba(255, 146, 0, 0.9)',
  loading: 'rgba(255,255,255,0.55)',
};

const STATUS_ICON: Record<HintStatus, string> = {
  ok:      '✓',
  warn:    '⚠',
  loading: '…',
};

interface Props {
  hints: CameraHints;
  allOk: boolean;
  frameWidth?: number;
  frameHeight?: number;
  autoShutterCountdown?: number | null;
}

function CameraGuideOverlay({
  hints,
  allOk,
  frameWidth = 280,
  frameHeight = 380,
  autoShutterCountdown = null,
}: Props) {
  const pulseAnim = useRef(new Animated.Value(1)).current;
  const borderColor = allOk ? '#00bf40' : '#ffffff';

  useEffect(() => {
    if (!allOk) return;
    const anim = Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, { toValue: 0.4, duration: 600, useNativeDriver: true }),
        Animated.timing(pulseAnim, { toValue: 1, duration: 600, useNativeDriver: true }),
      ]),
    );
    anim.start();
    return () => anim.stop();
  }, [allOk, pulseAnim]);

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      {/* 가이드 frame */}
      <View style={styles.frameArea}>
        <View style={[styles.frame, { width: frameWidth, height: frameHeight }]}>
          <View style={[styles.corner, styles.TL, { borderColor }]} />
          <View style={[styles.corner, styles.TR, { borderColor }]} />
          <View style={[styles.corner, styles.BL, { borderColor }]} />
          <View style={[styles.corner, styles.BR, { borderColor }]} />

          {/* 중앙 안내 문구 */}
          <View style={styles.centerHint}>
            <Text style={styles.centerHintTxt}>
              {allOk
                ? autoShutterCountdown != null
                  ? `${autoShutterCountdown}초 후 자동 촬영`
                  : '약봉투를 맞춰주세요'
                : '여기에 약봉투를 맞춰주세요'}
            </Text>
          </View>

          {/* allOk 시 펄스 원 */}
          {allOk && autoShutterCountdown != null && (
            <Animated.View style={[styles.countdownRing, { opacity: pulseAnim }]} />
          )}
        </View>
      </View>

      {/* hint 행 */}
      <View style={styles.hintRow}>
        {(Object.keys(hints) as Array<keyof CameraHints>).map((key) => {
          const status = hints[key];
          const meta = HINT_META[key];
          return (
            <View
              key={key}
              style={styles.hintPill}
              accessibilityLabel={`${meta.label} ${status}`}
            >
              <Text style={styles.hintIcon}>{meta.icon}</Text>
              <Text style={styles.hintLabel}>{meta.label}</Text>
              <Text style={[styles.hintStatus, { color: STATUS_COLOR[status] }]}>
                {STATUS_ICON[status]}
              </Text>
            </View>
          );
        })}
      </View>
    </View>
  );
}

const CORNER_SIZE = 32;
const CORNER_WIDTH = 3;

const styles = StyleSheet.create({
  frameArea: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  frame: {
    position: 'relative',
    alignItems: 'center',
    justifyContent: 'center',
  },
  corner: {
    position: 'absolute',
    width: CORNER_SIZE,
    height: CORNER_SIZE,
  },
  TL: {
    top: 0, left: 0,
    borderTopWidth: CORNER_WIDTH, borderLeftWidth: CORNER_WIDTH,
    borderTopLeftRadius: 8,
  },
  TR: {
    top: 0, right: 0,
    borderTopWidth: CORNER_WIDTH, borderRightWidth: CORNER_WIDTH,
    borderTopRightRadius: 8,
  },
  BL: {
    bottom: 0, left: 0,
    borderBottomWidth: CORNER_WIDTH, borderLeftWidth: CORNER_WIDTH,
    borderBottomLeftRadius: 8,
  },
  BR: {
    bottom: 0, right: 0,
    borderBottomWidth: CORNER_WIDTH, borderRightWidth: CORNER_WIDTH,
    borderBottomRightRadius: 8,
  },
  centerHint: {
    backgroundColor: 'rgba(0,0,0,0.45)',
    borderRadius: radius.full,
    paddingHorizontal: space.s16,
    paddingVertical: space.s8,
  },
  centerHintTxt: {
    color: '#fff',
    fontSize: scale(13),
    fontWeight: '600',
    textAlign: 'center',
  },
  countdownRing: {
    position: 'absolute',
    width: scale(80),
    height: scale(80),
    borderRadius: scale(40),
    borderWidth: 3,
    borderColor: '#00bf40',
  },
  hintRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: space.s8,
    paddingBottom: 120,
    paddingHorizontal: space.s16,
  },
  hintPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: 'rgba(0,0,0,0.55)',
    borderRadius: radius.full,
    paddingHorizontal: space.s12,
    paddingVertical: space.s6,
  },
  hintIcon: { fontSize: scale(12) },
  hintLabel: { color: 'rgba(255,255,255,0.8)', fontSize: scale(11), fontWeight: '600' },
  hintStatus: { fontSize: scale(12), fontWeight: '700' },
});

export default memo(CameraGuideOverlay);
