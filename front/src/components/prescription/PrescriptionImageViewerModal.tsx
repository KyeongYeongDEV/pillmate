import React, { useCallback } from 'react';
import { Modal, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import { GestureHandlerRootView, Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withTiming,
  runOnJS,
} from 'react-native-reanimated';
import { Image as ExpoImage } from 'expo-image';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import { colors, space, radius, scale as dp } from '@/styles/tokens';
import {
  clamp,
  maxTranslate,
  nextDoubleTapScale,
  MIN_SCALE,
  MAX_SCALE,
} from '@/lib/prescription/imageZoom';

const RESET_DURATION_MS = 180;
const CLOSE_TAP_MAX_DURATION_MS = 250;

interface Props {
  visible: boolean;
  url: string | null;
  onClose: () => void;
}

export default function PrescriptionImageViewerModal({ visible, url, onClose }: Props) {
  const { width, height } = useWindowDimensions();

  const scale = useSharedValue(1);
  const savedScale = useSharedValue(1);
  const translateX = useSharedValue(0);
  const translateY = useSharedValue(0);
  const savedTranslateX = useSharedValue(0);
  const savedTranslateY = useSharedValue(0);

  const resetTransform = useCallback(() => {
    scale.value = 1;
    savedScale.value = 1;
    translateX.value = 0;
    translateY.value = 0;
    savedTranslateX.value = 0;
    savedTranslateY.value = 0;
  }, [scale, savedScale, translateX, translateY, savedTranslateX, savedTranslateY]);

  const handleClose = useCallback(() => {
    resetTransform();
    onClose();
  }, [resetTransform, onClose]);

  const clampTranslate = useCallback(() => {
    'worklet';
    const boundX = maxTranslate(scale.value, width);
    const boundY = maxTranslate(scale.value, height);
    translateX.value = withTiming(clamp(translateX.value, -boundX, boundX), { duration: RESET_DURATION_MS });
    translateY.value = withTiming(clamp(translateY.value, -boundY, boundY), { duration: RESET_DURATION_MS });
    savedTranslateX.value = clamp(savedTranslateX.value, -boundX, boundX);
    savedTranslateY.value = clamp(savedTranslateY.value, -boundY, boundY);
  }, [scale, width, height, translateX, translateY, savedTranslateX, savedTranslateY]);

  const pinch = Gesture.Pinch()
    .onUpdate((e) => {
      scale.value = clamp(savedScale.value * e.scale, MIN_SCALE, MAX_SCALE);
    })
    .onEnd(() => {
      savedScale.value = scale.value;
      if (scale.value <= MIN_SCALE) {
        scale.value = withTiming(MIN_SCALE, { duration: RESET_DURATION_MS });
        savedScale.value = MIN_SCALE;
        translateX.value = withTiming(0, { duration: RESET_DURATION_MS });
        translateY.value = withTiming(0, { duration: RESET_DURATION_MS });
        savedTranslateX.value = 0;
        savedTranslateY.value = 0;
      } else {
        clampTranslate();
      }
    });

  const pan = Gesture.Pan()
    .onUpdate((e) => {
      translateX.value = savedTranslateX.value + e.translationX;
      translateY.value = savedTranslateY.value + e.translationY;
    })
    .onEnd(() => {
      savedTranslateX.value = translateX.value;
      savedTranslateY.value = translateY.value;
      clampTranslate();
    });

  const doubleTap = Gesture.Tap()
    .numberOfTaps(2)
    .onEnd(() => {
      const target = nextDoubleTapScale(scale.value);
      scale.value = withTiming(target, { duration: RESET_DURATION_MS });
      savedScale.value = target;
      if (target === MIN_SCALE) {
        translateX.value = withTiming(0, { duration: RESET_DURATION_MS });
        translateY.value = withTiming(0, { duration: RESET_DURATION_MS });
        savedTranslateX.value = 0;
        savedTranslateY.value = 0;
      }
    });

  const tapToClose = Gesture.Tap()
    .maxDuration(CLOSE_TAP_MAX_DURATION_MS)
    .onEnd(() => {
      if (scale.value <= MIN_SCALE) {
        runOnJS(handleClose)();
      }
    });

  const composed = Gesture.Simultaneous(
    pinch,
    pan,
    Gesture.Exclusive(doubleTap, tapToClose),
  );

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: translateX.value },
      { translateY: translateY.value },
      { scale: scale.value },
    ],
  }));

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={handleClose}
    >
      <SafeAreaProvider style={styles.root}>
        <GestureHandlerRootView style={styles.root}>
          <View style={styles.backdrop} />
          <GestureDetector gesture={composed}>
            <Animated.View style={[styles.imageWrap, { width, height }]}>
              {url ? (
                <ExpoImage
                  source={{ uri: url }}
                  style={[styles.image, animatedStyle]}
                  contentFit="contain"
                  cachePolicy="memory-disk"
                  accessibilityLabel="약봉투 이미지 확대"
                />
              ) : null}
            </Animated.View>
          </GestureDetector>
          <SafeAreaView style={styles.closeSafe} edges={['top']} pointerEvents="box-none">
            <Pressable
              style={styles.closeBtn}
              onPress={handleClose}
              accessibilityLabel="닫기"
              accessibilityRole="button"
              hitSlop={12}
            >
              <Feather name="x" size={dp(26)} color={colors.staticWhite} />
            </Pressable>
          </SafeAreaView>
        </GestureHandlerRootView>
      </SafeAreaProvider>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  backdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.96)' },
  imageWrap: { alignItems: 'center', justifyContent: 'center' },
  image: { width: '100%', height: '100%' },
  closeSafe: { position: 'absolute', top: 0, right: 0, left: 0 },
  closeBtn: {
    alignSelf: 'flex-end',
    margin: space.s12,
    width: dp(44),
    height: dp(44),
    borderRadius: radius.full,
    backgroundColor: 'rgba(0,0,0,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
