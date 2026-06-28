import React, { useRef, useState, useCallback, useEffect } from 'react';
import { View, Text, Pressable, StyleSheet, Alert, ActivityIndicator, Switch } from 'react-native';
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
import { useCameraGuide } from '@/hooks/useCameraGuide';
import { useOcrInFlight } from '@/hooks/useOcrInFlight';
import { downsizeForOcr } from '@/lib/imageProcessing';

const AUTO_SHUTTER_DELAY = 3;
const OCR_TYPICAL_LOW_SEC  = 20;
const OCR_TYPICAL_HIGH_SEC = 30;
const OCR_MAX_MIN          = 1;  // p95 실측 ~65초 기준 → 1분으로 안내

export default function CameraScreen() {
  const insets = useSafeAreaInsets();
  const [permission, requestPermission] = useCameraPermissions();
  const [flash, setFlash] = useState<'on' | 'off'>('off');
  const [loading, setLoading] = useState(false);
  const [ocrError, setOcrError] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [msgIdx, setMsgIdx] = useState(0);
  const elapsedIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const toggleIntervalRef  = useRef<ReturnType<typeof setInterval> | null>(null);
  const [autoShutter, setAutoShutter] = useState(false);
  const [countdown, setCountdown] = useState<number | null>(null);
  const cameraRef = useRef<CameraView>(null);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const dispatch = useAppDispatch();
  const { hints, allOk, reset, warnShake } = useCameraGuide();
  const { begin, end, hashImageUri } = useOcrInFlight();

  const processImage = useCallback(
    async (uri: string) => {
      const hash = await hashImageUri(uri);
      const { allowed, elapsedMs } = begin(hash);
      if (!allowed) {
        Alert.alert('이미 인식 중', `이미 인식 중입니다. ${Math.round(elapsedMs / 1000)}초 경과`);
        return;
      }
      setLoading(true);
      setOcrError(false);
      try {
        const processed = await downsizeForOcr(uri);
        const uploadResp = await prescriptionApi.issueUploadUrl({ contentType: 'image/jpeg' });
        const prescribedAt = new Date().toISOString().slice(0, 10);
        dispatch(setImageKey(uploadResp.objectKey));
        await prescriptionApi.uploadToS3(uploadResp.uploadUrl, processed.uri);
        const extractResp = await prescriptionApi.ocrExtract({
          prescribedAt,
          imageKey: uploadResp.objectKey,
        });
        dispatch(addFromExtract({ ...extractResp, prescribedAt, imageKey: uploadResp.objectKey }));
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
        router.replace('/prescription/review' as any);
      } catch {
        setLoading(false);
        setOcrError(true);
        reset();
      } finally {
        end(hash);
      }
    },
    [dispatch, reset, begin, end, hashImageUri],
  );

  const handleRetry = useCallback(() => {
    setOcrError(false);
  }, []);

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
    if (loading) {
      setElapsed(0);
      setMsgIdx(0);
      elapsedIntervalRef.current = setInterval(() => setElapsed(s => s + 1), 1000);
      toggleIntervalRef.current  = setInterval(() => setMsgIdx(m => 1 - m), 3500);
    } else {
      if (elapsedIntervalRef.current) { clearInterval(elapsedIntervalRef.current); elapsedIntervalRef.current = null; }
      if (toggleIntervalRef.current)  { clearInterval(toggleIntervalRef.current);  toggleIntervalRef.current  = null; }
    }
    return () => {
      if (elapsedIntervalRef.current) clearInterval(elapsedIntervalRef.current);
      if (toggleIntervalRef.current)  clearInterval(toggleIntervalRef.current);
    };
  }, [loading]);

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

  if (loading) {
    return (
      <View style={styles.loadingRoot}>
        <ActivityIndicator size="large" color="#fff" />
        {msgIdx === 0 ? (
          <>
            <Text style={styles.loadingTxt}>{`약을 인식하고 있어요 · ${elapsed}초`}</Text>
            <Text style={styles.loadingSub}>{`보통 ${OCR_TYPICAL_LOW_SEC}~${OCR_TYPICAL_HIGH_SEC}초, 최대 ${OCR_MAX_MIN}분 소요`}</Text>
          </>
        ) : (
          <>
            <Text style={styles.loadingTxt}>인식 결과를 꼭 확인해 주세요</Text>
            <Text style={styles.loadingSub}>{`인식 결과가 정확하지 않을 수 있어요.\n등록 후 약 정보를 꼭 확인해 주세요.`}</Text>
          </>
        )}
      </View>
    );
  }

  if (ocrError) {
    return (
      <View style={styles.loadingRoot}>
        <Text style={styles.errorIcon}>⚠️</Text>
        <Text style={styles.loadingTxt}>약 인식에 시간이 오래 걸려요</Text>
        <Text style={styles.loadingSub}>잠시 후 다시 시도해 주세요</Text>
        <View style={styles.errorActions}>
          <Pressable
            style={styles.retryBtn}
            onPress={handleRetry}
            accessibilityLabel="다시 시도"
            accessibilityRole="button"
          >
            <Text style={styles.retryBtnTxt}>다시 시도</Text>
          </Pressable>
          <Pressable
            style={styles.backBtn}
            onPress={() => safeBack('/prescription')}
            accessibilityLabel="뒤로"
            accessibilityRole="button"
          >
            <Text style={styles.backBtnTxt}>뒤로</Text>
          </Pressable>
        </View>
      </View>
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
  loadingRoot: { flex: 1, backgroundColor: '#0F0F10', alignItems: 'center', justifyContent: 'center', gap: space.s16, paddingHorizontal: space.s32 },
  loadingTxt: { ...typography.headline2, color: '#fff', textAlign: 'center' },
  loadingSub: { ...typography.body2r, color: 'rgba(255,255,255,0.6)', textAlign: 'center' },
  errorIcon: { fontSize: scale(40) },
  errorActions: { flexDirection: 'row', gap: space.s12, marginTop: space.s8 },
  retryBtn: {
    paddingHorizontal: space.s24, paddingVertical: space.s12,
    borderRadius: radius.r12, backgroundColor: colors.primaryNormal,
  },
  retryBtnTxt: { ...typography.body2n, color: '#fff', fontWeight: '700' },
  backBtn: {
    paddingHorizontal: space.s24, paddingVertical: space.s12,
    borderRadius: radius.r12, backgroundColor: 'rgba(255,255,255,0.18)',
  },
  backBtnTxt: { ...typography.body2n, color: '#fff' },
});
