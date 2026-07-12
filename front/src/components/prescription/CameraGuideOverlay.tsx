import React, { memo } from 'react';
import { View, Text, StyleSheet } from 'react-native';

interface Props {
  frameWidth?: number;
  frameHeight?: number;
}

// 미니멀 가이드 — 네모칸(4 corner) 프레임 + 촬영 유도 문구 1줄.
function CameraGuideOverlay({ frameWidth = 280, frameHeight = 380 }: Props) {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      <View style={styles.frameArea}>
        <View style={[styles.frame, { width: frameWidth, height: frameHeight }]}>
          <Text style={styles.guideText}>{GUIDE_TEXT}</Text>
          <Text style={styles.warnText}>{WARN_TEXT}</Text>
          <View style={[styles.corner, styles.TL]} />
          <View style={[styles.corner, styles.TR]} />
          <View style={[styles.corner, styles.BL]} />
          <View style={[styles.corner, styles.BR]} />
        </View>
      </View>
    </View>
  );
}

const CORNER_SIZE = 32;
const CORNER_WIDTH = 3;
const BORDER_COLOR = '#ffffff';
const GUIDE_TEXT = '알약 정보만 있으면 돼요!';
const WARN_TEXT = '⚠️ 주민번호는 가려주세요';
const GUIDE_GAP = 28;
const WARN_GAP = 20;
const GUIDE_FONT_SIZE = 16;
const WARN_FONT_SIZE = 14;

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
  guideText: {
    position: 'absolute',
    bottom: '100%',
    marginBottom: GUIDE_GAP,
    left: 0,
    right: 0,
    textAlign: 'center',
    color: BORDER_COLOR,
    fontSize: GUIDE_FONT_SIZE,
    fontWeight: '500',
    textShadowColor: 'rgba(0,0,0,0.5)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 2,
  },
  warnText: {
    position: 'absolute',
    top: '100%',
    marginTop: WARN_GAP,
    left: 0,
    right: 0,
    textAlign: 'center',
    color: BORDER_COLOR,
    fontSize: WARN_FONT_SIZE,
    fontWeight: '700',
    textShadowColor: 'rgba(0,0,0,0.5)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 2,
  },
  corner: {
    position: 'absolute',
    width: CORNER_SIZE,
    height: CORNER_SIZE,
    borderColor: BORDER_COLOR,
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
});

export default memo(CameraGuideOverlay);
