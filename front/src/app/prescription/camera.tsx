import React, { useRef, useState, useCallback, useEffect } from 'react';
import { View, Text, Pressable, StyleSheet, Alert, Switch } from 'react-native';
import { CameraView, CameraType, useCameraPermissions } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';
import * as Haptics from 'expo-haptics';
import { router } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import { useAppDispatch } from '@/store/hooks';
import { addFromExtract, setImageKey } from '@/store/slices/prescriptionFlowSlice';
import { prescriptionApi } from '@/lib/api/prescription';
import { safeBack } from '@/lib/router/safeBack';
import CameraGuideOverlay from '@/components/prescription/CameraGuideOverlay';
import OcrProgress from '@/components/prescription/OcrProgress';
import { useCameraGuide } from '@/hooks/useCameraGuide';
import { useOcrInFlight } from '@/hooks/useOcrInFlight';
import { downsizeForOcr } from '@/lib/imageProcessing';

const AUTO_SHUTTER_DELAY = 3;

export default function CameraScreen() {
  const insets = useSafeAreaInsets();
  const [permission, requestPermission] = useCameraPermissions();
  const [flash, setFlash] = useState<'on' | 'off'>('off');
  const [loading, setLoading] = useState(false);
  const [ocrError, setOcrError] = useState(false);
  const [lastProcessed, setLastProcessed] = useState<{ uri: string; imageKey: string } | null>(null);
  const attemptRef = useRef(0);
  const [autoShutter, setAutoShutter] = useState(false);
  const [countdown, setCountdown] = useState<number | null>(null);
  const cameraRef = useRef<CameraView>(null);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const dispatch = useAppDispatch();
  const { hints, allOk, reset, warnShake } = useCameraGuide();
  const { begin, end, hashImageUri } = useOcrInFlight();

  const uploadImage = useCallback(async (uri: string): Promise<string> => {
    const processed = await downsizeForOcr(uri);
    const uploadResp = await prescriptionApi.issueUploadUrl({ contentType: 'image/jpeg' });
    dispatch(setImageKey(uploadResp.objectKey));
    await prescriptionApi.uploadToS3(uploadResp.uploadUrl, processed.uri);
    setLastProcessed({ uri, imageKey: uploadResp.objectKey });
    return uploadResp.objectKey;
  }, [dispatch]);

  const runOcrExtract = useCallback(async (imageKey: string) => {
    const prescribedAt = new Date().toISOString().slice(0, 10);
    const extractResp = await prescriptionApi.ocrExtract({ prescribedAt, imageKey });
    dispatch(addFromExtract({ ...extractResp, prescribedAt, imageKey }));
  }, [dispatch]);

  const processImage = useCallback(
    async (uri: string) => {
      const hash = await hashImageUri(uri);
      const { allowed, elapsedMs } = begin(hash);
      if (!allowed) {
        Alert.alert('이미 인식 중', `이미 인식 중입니다. ${Math.round(elapsedMs / 1000)}초 경과`);
        return;
      }
      const attempt = ++attemptRef.current;
      setLoading(true);
      setOcrError(false);
      try {
        const imageKey = await uploadImage(uri);
        await runOcrExtract(imageKey);
        if (attemptRef.current !== attempt) return; // 사용자가 대기 중 취소
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
        router.replace('/prescription/review' as any);
      } catch {
        if (attemptRef.current !== attempt) return;
        setLoading(false);
        setOcrError(true);
        reset();
      } finally {
        end(hash);
      }
    },
    [begin, end, hashImageUri, uploadImage, runOcrExtract, reset],
  );

  // 기존 이미지(이미 S3 업로드됨)로 ocrExtract 만 재호출 — 재촬영/재업로드 없음
  const handleRetry = useCallback(async () => {
    if (!lastProcessed) {
      setOcrError(false);
      return;
    }
    const hash = await hashImageUri(lastProcessed.uri);
    const { allowed, elapsedMs } = begin(hash);
    if (!allowed) {
      Alert.alert('이미 인식 중', `이미 인식 중입니다. ${Math.round(elapsedMs / 1000)}초 경과`);
      return;
    }
    const attempt = ++attemptRef.current;
    setOcrError(false);
    setLoading(true);
    try {
      await runOcrExtract(lastProcessed.imageKey);
      if (attemptRef.current !== attempt) return;
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      router.replace('/prescription/review' as any);
    } catch {
      if (attemptRef.current !== attempt) return;
      setLoading(false);
      setOcrError(true);
    } finally {
      end(hash);
    }
  }, [lastProcessed, hashImageUri, begin, end, runOcrExtract]);

  const handleAbandon = useCallback(() => {
    attemptRef.current += 1; // 진행 중 attempt 의 UI 효과 무효화
    setLoading(false);
    setOcrError(false);
    reset();
  }, [reset]);

  const capture = useCallback(async () => {
    if (!cameraRef.current) return;
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const photo = await cameraRef.current.takePictureAsync({ quality: 0.9 });
    if (photo) await processImage(photo.uri);
  }, [processImage]);

  const stopCountdown = useCallback(() => {
    if (countdownRef.current) clearInterval(countdownRef.current);
    countdownRef.current = null;
    setCountdown(null);
  }, []);

  useEffect(() => {
    if (!autoShutter || !allOk) { stopCountdown(); return; }
    let remaining = AUTO_SHUTTER_DELAY;
    setCountdown(remaining);
    countdownRef.current = setInterval(() => {
      remaining -= 1;
      if (remaining <= 0) {
        stopCountdown();
        capture();
      } else {
        setCountdown(remaining);
      }
    }, 1000);
    return stopCountdown;
  }, [autoShutter, allOk, capture, stopCountdown]);

  const handleGallery = useCallback(async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') return;
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.9 });
    if (!result.canceled) await processImage(result.assets[0].uri);
  }, [processImage]);

  if (!permission?.granted) {
    return (
      <View style={styles.permRoot}>
        <Text style={styles.permText}>카메라 권한이 필요합니다</Text>
        <Pressable onPress={requestPermission} style={styles.permBtn} accessibilityRole="button">
          <Text style={styles.permBtnTxt}>권한 허용</Text>
        </Pressable>
      </View>
    );
  }

  if (loading || ocrError) {
    return (
      <OcrProgress
        phase={ocrError ? 'failed' : 'progressing'}
        onRetry={ocrError ? handleRetry : handleAbandon}
        onBack={handleAbandon}
      />
    );
  }

  return (
    <View style={styles.root}>
      <CameraView
        ref={cameraRef}
        style={StyleSheet.absoluteFill}
        facing={'back' as CameraType}
        enableTorch={flash === 'on'}
      />

      <CameraGuideOverlay hints={hints} allOk={allOk} autoShutterCountdown={countdown} />

      <View style={[styles.top, { top: insets.top + 16 }]}>
        <Pressable onPress={() => safeBack('/prescription')} style={styles.circle} accessibilityLabel="닫기" accessibilityRole="button">
          <Text style={styles.circleIcon}>✕</Text>
        </Pressable>
        <View style={styles.aiBadge}>
          <Text style={styles.aiBadgeTxt}>✨ AI 자동 인식</Text>
        </View>
        <Pressable onPress={() => setFlash(f => (f === 'on' ? 'off' : 'on'))} style={styles.circle} accessibilityLabel="플래시 토글" accessibilityRole="button">
          <Text style={styles.circleIcon}>{flash === 'on' ? '🔦' : '💡'}</Text>
        </Pressable>
      </View>

      <View style={[styles.autoShutterRow, { top: insets.top + 72 }]}>
        <Text style={styles.autoShutterLabel}>자동 촬영</Text>
        <Switch
          value={autoShutter}
          onValueChange={setAutoShutter}
          trackColor={{ false: 'rgba(255,255,255,0.2)', true: colors.primaryNormal }}
          thumbColor="#fff"
          accessibilityLabel="자동 촬영 토글"
        />
      </View>

      <View style={[styles.bottom, { bottom: insets.bottom + 24 }]}>
        <Pressable onPress={handleGallery} style={styles.galleryBtn} accessibilityLabel="갤러리" accessibilityRole="button">
          <Text style={styles.galleryIcon}>🖼</Text>
        </Pressable>
        <Pressable onPress={capture} style={styles.shutter} accessibilityLabel="촬영" accessibilityRole="button">
          <View style={styles.shutterInner} />
        </Pressable>
        <Pressable onPress={() => router.push('/prescription/manual' as any)} style={styles.manualBtn} accessibilityLabel="수동 입력" accessibilityRole="button">
          <Text style={styles.manualIcon}>✏️</Text>
          <Text style={styles.manualTxt}>수동</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0F0F10' },
  top: {
    position: 'absolute', top: 60, left: 0, right: 0, zIndex: 10,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16,
  },
  circle: {
    width: scale(40), height: scale(40), borderRadius: scale(20),
    backgroundColor: 'rgba(255,255,255,0.18)', alignItems: 'center', justifyContent: 'center',
  },
  circleIcon: { fontSize: scale(18), color: '#fff' },
  aiBadge: {
    paddingHorizontal: space.s16, paddingVertical: space.s8,
    borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.18)',
  },
  aiBadgeTxt: { ...typography.label2, color: '#fff', fontWeight: '600' },
  autoShutterRow: {
    position: 'absolute', top: 116, right: space.s16, zIndex: 10,
    flexDirection: 'row', alignItems: 'center', gap: space.s8,
    backgroundColor: 'rgba(0,0,0,0.45)', borderRadius: radius.full,
    paddingHorizontal: space.s12, paddingVertical: space.s6,
  },
  autoShutterLabel: { color: '#fff', fontSize: scale(12), fontWeight: '600' },
  bottom: {
    position: 'absolute', bottom: 50, left: 0, right: 0, zIndex: 10,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s32,
  },
  galleryBtn: {
    width: scale(52), height: scale(52), borderRadius: radius.r12,
    backgroundColor: 'rgba(255,255,255,0.18)', alignItems: 'center', justifyContent: 'center',
  },
  galleryIcon: { fontSize: scale(24) },
  shutter: {
    width: scale(76), height: scale(76), borderRadius: scale(38), backgroundColor: '#fff',
    alignItems: 'center', justifyContent: 'center',
    borderWidth: 4, borderColor: 'rgba(255,255,255,0.25)',
  },
  shutterInner: { width: scale(62), height: scale(62), borderRadius: scale(31), backgroundColor: '#fff', borderWidth: 2, borderColor: '#0F0F10' },
  manualBtn: {
    width: scale(52), height: scale(52), borderRadius: radius.r12,
    backgroundColor: 'rgba(255,255,255,0.18)', alignItems: 'center', justifyContent: 'center',
  },
  manualIcon: { fontSize: scale(18) },
  manualTxt: { ...typography.caption1, color: '#fff', fontWeight: '600' },
  permRoot: { flex: 1, backgroundColor: colors.bgAlt, alignItems: 'center', justifyContent: 'center', gap: space.s16 },
  permText: { ...typography.body1n, color: colors.labelNormal },
  permBtn: { backgroundColor: colors.primaryNormal, borderRadius: radius.r12, paddingHorizontal: space.s24, paddingVertical: space.s12 },
  permBtnTxt: { ...typography.body1n, color: '#fff', fontWeight: '700' },
});
