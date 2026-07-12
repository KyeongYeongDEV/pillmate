import React, { useRef, useState, useCallback } from 'react';
import { View, Text, Pressable, StyleSheet, Alert, Image } from 'react-native';
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
import { useOcrInFlight } from '@/hooks/useOcrInFlight';
import { PII_BLOCK_ALERT_TITLE, PII_BLOCK_ALERT_MESSAGE } from '@/lib/constants';
import { downsizeForOcr } from '@/lib/imageProcessing';

export default function CameraScreen() {
  const insets = useSafeAreaInsets();
  const [permission, requestPermission] = useCameraPermissions();
  const [flash, setFlash] = useState<'on' | 'off'>('off');
  const [loading, setLoading] = useState(false);
  const [ocrError, setOcrError] = useState(false);
  const [lastProcessed, setLastProcessed] = useState<{ uri: string; imageKey: string } | null>(null);
  const [previewUri, setPreviewUri] = useState<string | null>(null);
  const attemptRef = useRef(0);
  const cameraRef = useRef<CameraView>(null);
  const dispatch = useAppDispatch();
  const { begin, end, hashImageUri } = useOcrInFlight();

  const uploadImage = useCallback(async (uri: string): Promise<string> => {
    const processed = await downsizeForOcr(uri);
    const uploadResp = await prescriptionApi.issueUploadUrl({ contentType: 'image/jpeg' });
    dispatch(setImageKey(uploadResp.objectKey));
    await prescriptionApi.uploadToS3(uploadResp.uploadUrl, processed.uri);
    setLastProcessed({ uri, imageKey: uploadResp.objectKey });
    return uploadResp.objectKey;
  }, [dispatch]);

  const runOcrExtract = useCallback(async (imageKey: string): Promise<boolean> => {
    const prescribedAt = new Date().toISOString().slice(0, 10);
    const extractResp = await prescriptionApi.ocrExtract({ prescribedAt, imageKey });
    if (extractResp.piiDetected) {
      Alert.alert(PII_BLOCK_ALERT_TITLE, PII_BLOCK_ALERT_MESSAGE);
      return false;
    }
    dispatch(addFromExtract({ ...extractResp, prescribedAt, imageKey }));
    return true;
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
        const proceed = await runOcrExtract(imageKey);
        if (attemptRef.current !== attempt) return; // 사용자가 대기 중 취소
        if (!proceed) { setLoading(false); return; } // 주민번호 감지 — 재촬영 유도
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
        router.replace('/prescription/review' as any);
      } catch {
        if (attemptRef.current !== attempt) return;
        setLoading(false);
        setOcrError(true);
      } finally {
        end(hash);
      }
    },
    [begin, end, hashImageUri, uploadImage, runOcrExtract],
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
      const proceed = await runOcrExtract(lastProcessed.imageKey);
      if (attemptRef.current !== attempt) return;
      if (!proceed) { setLoading(false); return; } // 주민번호 감지 — 재촬영 유도
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
  }, []);

  const capture = useCallback(async () => {
    if (!cameraRef.current) return;
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const photo = await cameraRef.current.takePictureAsync({ quality: 0.9 });
    if (photo) setPreviewUri(photo.uri);
  }, []);

  const handleRetake = useCallback(() => {
    setPreviewUri(null);
  }, []);

  const handleConfirmUse = useCallback(() => {
    if (!previewUri) return;
    processImage(previewUri);
  }, [previewUri, processImage]);

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

  if (previewUri) {
    return (
      <View style={styles.root}>
        <Image source={{ uri: previewUri }} style={StyleSheet.absoluteFill} resizeMode="cover" />

        <View style={[styles.top, { top: insets.top + 16 }]}>
          <Pressable onPress={() => safeBack('/prescription')} style={styles.circle} accessibilityLabel="취소" accessibilityRole="button">
            <Text style={styles.circleIcon}>✕</Text>
          </Pressable>
          <View style={styles.previewHint}>
            <Text style={styles.previewHintText}>글자가 선명한지 확인해 주세요</Text>
          </View>
        </View>

        <View style={[styles.previewBottom, { bottom: insets.bottom + 24 }]}>
          <Pressable onPress={handleRetake} style={styles.previewBtnSecondary} accessibilityLabel="다시 찍기" accessibilityRole="button">
            <Text style={styles.previewBtnSecondaryText}>다시 찍기</Text>
          </Pressable>
          <Pressable onPress={handleConfirmUse} style={styles.previewBtnPrimary} accessibilityLabel="사용하기" accessibilityRole="button">
            <Text style={styles.previewBtnPrimaryText}>사용하기</Text>
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

      <CameraGuideOverlay />

      <View style={[styles.top, { top: insets.top + 16 }]}>
        <Pressable onPress={() => safeBack('/prescription')} style={styles.circle} accessibilityLabel="닫기" accessibilityRole="button">
          <Text style={styles.circleIcon}>✕</Text>
        </Pressable>
        <Pressable onPress={() => setFlash(f => (f === 'on' ? 'off' : 'on'))} style={styles.circle} accessibilityLabel="플래시 토글" accessibilityRole="button">
          <Text style={styles.circleIcon}>{flash === 'on' ? '🔦' : '💡'}</Text>
        </Pressable>
      </View>

      <View style={[styles.bottom, { bottom: insets.bottom + 24 }]}>
        <Pressable onPress={handleGallery} style={styles.galleryBtn} accessibilityLabel="갤러리" accessibilityRole="button">
          <Text style={styles.galleryIcon}>🖼</Text>
        </Pressable>
        <Pressable onPress={capture} style={styles.shutter} accessibilityLabel="촬영" accessibilityRole="button">
          <View style={styles.shutterInner} />
        </Pressable>
        <View style={styles.bottomSpacer} />
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
  previewHint: {
    flex: 1, alignItems: 'center', marginLeft: -scale(40),
  },
  previewHintText: {
    ...typography.body2n, color: '#fff', backgroundColor: 'rgba(0,0,0,0.35)',
    paddingHorizontal: space.s12, paddingVertical: space.s6, borderRadius: radius.r8,
    overflow: 'hidden',
  },
  previewBottom: {
    position: 'absolute', left: 0, right: 0, zIndex: 10,
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
    paddingHorizontal: space.s16,
  },
  previewBtnSecondary: {
    flex: 1, height: scale(52), borderRadius: radius.r12,
    backgroundColor: 'rgba(255,255,255,0.18)', alignItems: 'center', justifyContent: 'center',
  },
  previewBtnSecondaryText: { ...typography.body1n, color: '#fff', fontWeight: '700' },
  previewBtnPrimary: {
    flex: 1, height: scale(52), borderRadius: radius.r12,
    backgroundColor: colors.primaryNormal, alignItems: 'center', justifyContent: 'center',
  },
  previewBtnPrimaryText: { ...typography.body1n, color: '#fff', fontWeight: '700' },
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
  bottomSpacer: { width: scale(52), height: scale(52) },
  permRoot: { flex: 1, backgroundColor: colors.bgAlt, alignItems: 'center', justifyContent: 'center', gap: space.s16 },
  permText: { ...typography.body1n, color: colors.labelNormal },
  permBtn: { backgroundColor: colors.primaryNormal, borderRadius: radius.r12, paddingHorizontal: space.s24, paddingVertical: space.s12 },
  permBtnTxt: { ...typography.body1n, color: '#fff', fontWeight: '700' },
});
