import React, { useCallback, useState } from 'react';
import { View, Text, StyleSheet, Pressable, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as Haptics from 'expo-haptics';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { scale, colors, space, radius, typography } from '@/styles/tokens';
import { API_BASE_URL } from '@/lib/api/client';
import { getToken, getCurrentUserId } from '@/lib/auth/storage';
import { extractInviteCode } from '@/lib/inviteCode';
import { safeBack } from '@/lib/router/safeBack';
import type { ApiEnvelope } from '@/lib/api/client';

export default function ScanGroupQrScreen() {
  const [permission, requestPermission] = useCameraPermissions();
  const [scanned, setScanned] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleScanned = useCallback(
    async ({ data }: { data: string }) => {
      if (scanned) return;
      setScanned(true);
      setError(null);
      const code = extractInviteCode(data);
      if (!code) {
        setError('PillMate 초대 QR이 아니에요');
        setTimeout(() => setScanned(false), 1500);
        return;
      }
      try {
        const groupId = await joinGroup(code);
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
        router.replace(`/group/${groupId}` as any);
      } catch (e: any) {
        setError(e?.message ?? '가입 실패');
        setTimeout(() => setScanned(false), 1500);
      }
    },
    [scanned],
  );

  if (!permission) {
    return (
      <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
        <Header />
        <ActivityIndicator color={colors.primaryBase} style={styles.loader} />
      </SafeAreaView>
    );
  }

  if (!permission.granted) {
    return (
      <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
        <Header />
        <View style={styles.permBody}>
          <Feather name="camera-off" size={scale(40)} color={colors.labelAssistive} />
          <Text style={styles.permTitle}>카메라 권한이 필요해요</Text>
          <Text style={styles.permDesc}>QR 스캔으로 그룹에 가입하려면{'\n'}카메라 접근을 허용해 주세요.</Text>
          <Pressable
            style={styles.permBtn}
            onPress={requestPermission}
            accessibilityLabel="권한 허용"
            accessibilityRole="button"
          >
            <Text style={styles.permBtnText}>권한 허용</Text>
          </Pressable>
          <Pressable
            style={styles.permFallback}
            onPress={() => router.replace('/group/join')}
            accessibilityLabel="코드 직접 입력"
            accessibilityRole="button"
          >
            <Text style={styles.permFallbackText}>코드로 직접 입력</Text>
          </Pressable>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <Header />
      <View style={styles.cameraWrap}>
        <CameraView
          style={StyleSheet.absoluteFill}
          facing="back"
          barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
          onBarcodeScanned={scanned ? undefined : handleScanned}
        />
        <View style={styles.frame} pointerEvents="none">
          <View style={styles.reticle} />
          <Text style={styles.hint}>가족이 공유한 QR 코드를 비춰주세요</Text>
        </View>
        {error && (
          <View style={styles.errorBanner}>
            <Text style={styles.errorText}>{error}</Text>
          </View>
        )}
        <Pressable
          style={styles.manualBtn}
          onPress={() => router.replace('/group/join')}
          accessibilityLabel="코드 직접 입력"
          accessibilityRole="button"
        >
          <Text style={styles.manualBtnText}>코드로 직접 입력</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

function Header() {
  return (
    <View style={styles.header}>
      <Pressable onPress={() => safeBack('/(tabs)/group')} accessibilityLabel="뒤로가기" accessibilityRole="button" hitSlop={8}>
        <Feather name="x" size={scale(22)} color={colors.labelNormal} />
      </Pressable>
      <Text style={styles.headerTitle}>QR로 그룹 가입</Text>
      <View style={{ width: scale(22) }} />
    </View>
  );
}

async function joinGroup(code: string): Promise<number> {
  const token = await getToken();
  const userId = await getCurrentUserId();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (userId != null) headers['X-User-Id'] = String(userId);

  const res = await fetch(`${API_BASE_URL}/groups/join/${code}`, { method: 'POST', headers });
  const envelope: ApiEnvelope<{ groupId: number }> = await res.json();
  if (!res.ok) {
    throw new Error(envelope?.error?.message ?? '초대 코드가 올바르지 않아요');
  }
  return envelope.data?.groupId ?? 0;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgNormal },
  loader: { flex: 1 },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  cameraWrap: { flex: 1, backgroundColor: '#000', position: 'relative' },
  frame: {
    position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
    alignItems: 'center', justifyContent: 'center', gap: space.s24,
  },
  reticle: {
    width: scale(240), height: scale(240), borderRadius: radius.r20,
    borderWidth: 2, borderColor: colors.staticWhite,
  },
  hint: {
    color: colors.staticWhite, fontSize: scale(14), fontWeight: '600',
    paddingHorizontal: space.s14, paddingVertical: space.s8,
    backgroundColor: 'rgba(0,0,0,0.5)', borderRadius: radius.full,
  },
  errorBanner: {
    position: 'absolute', top: space.s16, alignSelf: 'center',
    paddingHorizontal: space.s14, paddingVertical: space.s8,
    borderRadius: radius.r10, backgroundColor: colors.statusNegative,
  },
  errorText: { color: colors.staticWhite, fontSize: scale(13), fontWeight: '600' },
  manualBtn: {
    position: 'absolute', bottom: space.s24, alignSelf: 'center',
    paddingHorizontal: space.s20, paddingVertical: space.s12,
    borderRadius: radius.full, backgroundColor: 'rgba(255,255,255,0.92)',
  },
  manualBtnText: { fontSize: scale(14), fontWeight: '700', color: colors.labelNormal },
  permBody: {
    flex: 1, alignItems: 'center', justifyContent: 'center',
    padding: space.s24, gap: space.s12,
  },
  permTitle: { fontSize: scale(18), fontWeight: '700', color: colors.labelNormal },
  permDesc: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center', lineHeight: scale(20) },
  permBtn: {
    marginTop: space.s8, height: scale(48), paddingHorizontal: space.s24,
    borderRadius: radius.r12, backgroundColor: colors.primaryBase,
    alignItems: 'center', justifyContent: 'center',
  },
  permBtnText: { fontSize: scale(15), fontWeight: '700', color: colors.staticWhite },
  permFallback: {
    height: scale(48), paddingHorizontal: space.s24,
    alignItems: 'center', justifyContent: 'center',
  },
  permFallbackText: { fontSize: scale(14), fontWeight: '600', color: colors.primaryBase },
});
