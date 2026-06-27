import React, { useRef, useState, useCallback } from 'react';
import { View, Text, Pressable, StyleSheet, ActivityIndicator, Alert } from 'react-native';
import { CameraView, CameraType, useCameraPermissions } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';
import * as Haptics from 'expo-haptics';
import { router, useLocalSearchParams } from 'expo-router';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import { useAppDispatch } from '@/store/hooks';
import { addFromExtract, setImageKey } from '@/store/slices/prescriptionFlowSlice';
import { prescriptionApi } from '@/lib/api/prescription';
import { safeBack } from '@/lib/router/safeBack';
import { useOcrInFlight } from '@/hooks/useOcrInFlight';
import { downsizeForOcr } from '@/lib/imageProcessing';

const OCR_TYPICAL_LOW_SEC  = 20;
const OCR_TYPICAL_HIGH_SEC = 30;
const OCR_MAX_MIN          = 1;  // p95 실측 ~65초 기준 → 1분으로 안내

export default function ScanScreen() {
  const { galleryUri } = useLocalSearchParams<{ galleryUri?: string }>();
  const [permission, requestPermission] = useCameraPermissions();
  const [flash, setFlash] = useState<'on' | 'off'>('off');
  const [loading, setLoading] = useState(false);
  const [ocrError, setOcrError] = useState(false);
  const cameraRef = useRef<CameraView>(null);
  const dispatch = useAppDispatch();
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
        const uploadResp = await prescriptionApi.issueUploadUrl({
          contentType: 'image/jpeg',
        });
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
      } finally {
        end(hash);
      }
    },
    [dispatch, begin, end, hashImageUri],
  );

  const handleRetry = useCallback(() => {
    setOcrError(false);
  }, []);

  const handleShutter = useCallback(async () => {
    if (!cameraRef.current) return;
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const photo = await cameraRef.current.takePictureAsync({ quality: 0.9 });
    if (photo) await processImage(photo.uri);
  }, [processImage]);

  const handleGallery = useCallback(async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') return;
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.9 });
    if (!result.canceled) await processImage(result.assets[0].uri);
  }, [processImage]);

  // 갤러리 URI 가 있으면 바로 처리
  React.useEffect(() => {
    if (galleryUri) processImage(galleryUri);
  }, [galleryUri, processImage]);

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
        <Text style={styles.loadingTxt}>약을 인식하고 있어요</Text>
        <Text style={styles.loadingSub}>
          {`보통 ${OCR_TYPICAL_LOW_SEC}~${OCR_TYPICAL_HIGH_SEC}초, 최대 ${OCR_MAX_MIN}분 정도 걸릴 수 있어요`}
        </Text>
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
      <CameraView ref={cameraRef} style={StyleSheet.absoluteFill} facing={'back' as CameraType} enableTorch={flash === 'on'} />

      {/* 상단 컨트롤 */}
      <View style={styles.top}>
        <Pressable onPress={() => safeBack('/prescription')} style={styles.circle} accessibilityLabel="닫기" accessibilityRole="button">
          <Text style={styles.circleIcon}>✕</Text>
        </Pressable>
        <View style={styles.aiBadge}>
          <Text style={styles.aiBadgeTxt}>✨ AI 자동 인식</Text>
        </View>
        <Pressable onPress={() => setFlash(f => f === 'on' ? 'off' : 'on')} style={styles.circle} accessibilityLabel="플래시 토글" accessibilityRole="button">
          <Text style={styles.circleIcon}>{flash === 'on' ? '🔦' : '💡'}</Text>
        </Pressable>
      </View>

      {/* 가이드 프레임 */}
      <View style={styles.frameArea}>
        <View style={styles.frame}>
          <View style={[styles.corner, styles.cornerTL]} />
          <View style={[styles.corner, styles.cornerTR]} />
          <View style={[styles.corner, styles.cornerBL]} />
          <View style={[styles.corner, styles.cornerBR]} />
          <View style={styles.hintPill}>
            <Text style={styles.hintTxt}>약봉투를 사각형 안에 맞춰주세요</Text>
          </View>
        </View>
      </View>

      {/* 하단 컨트롤 */}
      <View style={styles.bottom}>
        <Pressable onPress={handleGallery} style={styles.galleryBtn} accessibilityLabel="갤러리" accessibilityRole="button">
          <Text style={styles.galleryIcon}>🖼</Text>
        </Pressable>
        <Pressable onPress={handleShutter} style={styles.shutter} accessibilityLabel="촬영" accessibilityRole="button">
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
    backgroundColor: 'rgba(255,255,255,0.18)',
    alignItems: 'center', justifyContent: 'center',
  },
  circleIcon: { fontSize: scale(18), color: '#fff' },
  aiBadge: {
    paddingHorizontal: space.s16, paddingVertical: space.s8,
    borderRadius: radius.full,
    backgroundColor: 'rgba(255,255,255,0.18)',
  },
  aiBadgeTxt: { ...typography.label2, color: '#fff', fontWeight: '600' },
  frameArea: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  frame: { width: scale(280), height: scale(370), position: 'relative', alignItems: 'center', justifyContent: 'center' },
  corner: { position: 'absolute', width: scale(36), height: scale(36) },
  cornerTL: { top: 0, left: 0, borderTopWidth: 3, borderLeftWidth: 3, borderColor: '#fff', borderTopLeftRadius: 8 },
  cornerTR: { top: 0, right: 0, borderTopWidth: 3, borderRightWidth: 3, borderColor: '#fff', borderTopRightRadius: 8 },
  cornerBL: { bottom: 0, left: 0, borderBottomWidth: 3, borderLeftWidth: 3, borderColor: '#fff', borderBottomLeftRadius: 8 },
  cornerBR: { bottom: 0, right: 0, borderBottomWidth: 3, borderRightWidth: 3, borderColor: '#fff', borderBottomRightRadius: 8 },
  hintPill: {
    position: 'absolute', top: -48,
    paddingHorizontal: space.s16, paddingVertical: space.s8,
    borderRadius: radius.full, backgroundColor: 'rgba(0,0,0,0.55)',
  },
  hintTxt: { ...typography.label2, color: '#fff' },
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
