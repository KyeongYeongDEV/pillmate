import React, { useCallback, useEffect, useState } from 'react';
import { View, Text, Pressable, StyleSheet, Linking } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { colors, typography, space, radius, scale } from '@/styles/tokens';
import { usePushPermissionStatus } from '@/hooks/usePushPermissionStatus';
import {
  getPushGuideDismissedAt,
  savePushGuideDismissedAt,
  shouldShowPushGuide,
} from '@/lib/notifications/permissionGuide';

const BANNER_TITLE = '복약 알림을 받을 수 없어요';
const BANNER_BODY = '기기에서 알림이 꺼져 있어요. 설정에서 알림을 켜면 복약 시간에 알려드려요.';
const OPEN_SETTINGS_LABEL = '설정 열기';
const DISMISS_LABEL = '나중에';

export default function PushPermissionBanner() {
  const status = usePushPermissionStatus();
  const [dismissedAt, setDismissedAt] = useState<number | null | undefined>(undefined);

  useEffect(() => {
    void getPushGuideDismissedAt().then(setDismissedAt);
  }, []);

  const handleOpenSettings = useCallback(() => {
    void Linking.openSettings();
  }, []);

  const handleDismiss = useCallback(() => {
    const now = Date.now();
    setDismissedAt(now);
    void savePushGuideDismissedAt(now);
  }, []);

  if (dismissedAt === undefined) return null;
  if (!shouldShowPushGuide(status, dismissedAt, Date.now())) return null;

  return (
    <View style={styles.banner} accessibilityLabel={BANNER_TITLE}>
      <View style={styles.row}>
        <View style={styles.icon}>
          <Feather name="bell-off" size={scale(18)} color={colors.orange40} />
        </View>
        <View style={styles.textArea}>
          <Text style={styles.title}>{BANNER_TITLE}</Text>
          <Text style={styles.body}>{BANNER_BODY}</Text>
        </View>
      </View>
      <View style={styles.actions}>
        <Pressable
          style={styles.dismissBtn}
          onPress={handleDismiss}
          accessibilityRole="button"
          accessibilityLabel={DISMISS_LABEL}
          hitSlop={8}
        >
          <Text style={styles.dismissTxt}>{DISMISS_LABEL}</Text>
        </Pressable>
        <Pressable
          style={styles.settingsBtn}
          onPress={handleOpenSettings}
          accessibilityRole="button"
          accessibilityLabel={OPEN_SETTINGS_LABEL}
          accessibilityHint="기기 설정의 앱 알림 화면으로 이동합니다"
        >
          <Text style={styles.settingsTxt}>{OPEN_SETTINGS_LABEL}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    gap: space.s12,
    padding: space.s14,
    borderRadius: radius.r16,
    backgroundColor: colors.orange95,
    borderWidth: 1,
    borderColor: '#F5D8A6',
  },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: space.s12 },
  icon: {
    width: scale(32),
    height: scale(32),
    borderRadius: radius.full,
    backgroundColor: colors.staticWhite,
    alignItems: 'center',
    justifyContent: 'center',
  },
  textArea: { flex: 1, gap: scale(2) },
  title: { ...typography.label1n, fontWeight: '700', color: colors.labelNormal },
  body: { ...typography.caption1, color: colors.labelAlternative, lineHeight: scale(18) },
  actions: { flexDirection: 'row', justifyContent: 'flex-end', alignItems: 'center', gap: space.s8 },
  dismissBtn: { paddingHorizontal: space.s12, paddingVertical: space.s8 },
  dismissTxt: { ...typography.label2, fontWeight: '600', color: colors.labelAlternative },
  settingsBtn: {
    paddingHorizontal: space.s16,
    paddingVertical: space.s8,
    borderRadius: radius.r12,
    backgroundColor: colors.orange40,
  },
  settingsTxt: { ...typography.label2, fontWeight: '700', color: colors.staticWhite },
});
