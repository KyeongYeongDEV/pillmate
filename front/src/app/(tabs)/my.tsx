import React from 'react';
import {
  View, Text, Pressable, ScrollView, StyleSheet, Alert, Linking,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import Constants from 'expo-constants';
import { clearAuth } from '@/lib/auth/storage';
import { colors, space, scale, radius, typography, shadows } from '@/styles/tokens';

const TERMS_URL = 'https://pillmate.app/terms';
const PRIVACY_URL = 'https://pillmate.app/privacy';
const APP_VERSION = Constants.expoConfig?.version ?? '1.0.0';

// ── Sub-components ────────────────────────────────────────────────────────────

interface SectionProps {
  label: string;
  children: React.ReactNode;
}

function Section({ label, children }: SectionProps) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionLabel}>{label}</Text>
      <View style={styles.menuCard}>{children}</View>
    </View>
  );
}

interface RowProps {
  label: string;
  right?: React.ReactNode;
  onPress?: () => void;
  danger?: boolean;
  showDivider?: boolean;
}

function Row({ label, right, onPress, danger, showDivider }: RowProps) {
  return (
    <>
      {showDivider && <View style={styles.divider} />}
      <Pressable
        style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
        onPress={onPress}
        disabled={!onPress}
        accessibilityRole={onPress ? 'button' : 'text'}
        accessibilityLabel={label}
      >
        <Text style={[styles.rowLabel, danger && styles.rowDanger]}>{label}</Text>
        <View style={styles.rowRight}>
          {right ?? (
            onPress && <Feather name="chevron-right" size={scale(16)} color={colors.labelAssistive} />
          )}
        </View>
      </Pressable>
    </>
  );
}

// ── Screen ────────────────────────────────────────────────────────────────────

export default function MyScreen() {
  function handleLogout() {
    Alert.alert(
      '로그아웃',
      '정말 로그아웃하시겠어요?',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '로그아웃',
          style: 'destructive',
          onPress: async () => {
            await clearAuth();
            router.replace('/(auth)/login');
          },
        },
      ],
      { cancelable: true },
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* Header */}
      <View style={styles.header}>
        <Pressable
          onPress={() => router.back()}
          accessibilityLabel="뒤로가기"
          accessibilityRole="button"
          hitSlop={8}
        >
          <Feather name="chevron-left" size={scale(24)} color={colors.labelNormal} />
        </Pressable>
        <Text style={styles.headerTitle}>설정</Text>
        <View style={styles.headerSpacer} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        {/* Profile card — 목업대로 고정 문구 */}
        <View style={styles.profileCard}>
          <View style={styles.avatar}>
            <Feather name="user" size={scale(26)} color={colors.primaryNormal} />
          </View>
          <View style={styles.profileInfo}>
            <Text style={styles.profileName}>내 계정</Text>
            <Text style={styles.profileSub}>PillMate 회원</Text>
          </View>
        </View>

        {/* 정보 섹션 */}
        <Section label="정보">
          <Row
            label="이용약관"
            onPress={() => Linking.openURL(TERMS_URL)}
          />
          <Row
            label="개인정보 처리방침"
            onPress={() => Linking.openURL(PRIVACY_URL)}
            showDivider
          />
          <Row
            label="앱 버전"
            right={<Text style={styles.versionTxt}>{APP_VERSION}</Text>}
            showDivider
          />
        </Section>

        {/* 계정 섹션 */}
        <Section label="계정">
          <Row label="로그아웃" onPress={handleLogout} danger />
        </Section>
      </ScrollView>
    </SafeAreaView>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgAlt },

  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  headerSpacer: { width: scale(24) },

  scroll: { padding: space.s16, gap: space.s16, paddingBottom: space.s48 },

  profileCard: {
    flexDirection: 'row', alignItems: 'center', gap: space.s14,
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line,
    padding: space.s16, ...shadows.small,
  },
  avatar: {
    width: scale(52), height: scale(52), borderRadius: scale(26),
    backgroundColor: colors.blue95,
    alignItems: 'center', justifyContent: 'center',
  },
  profileInfo: { gap: space.s2 },
  profileName: { ...typography.headline1, color: colors.labelNormal },
  profileSub: { ...typography.caption1, color: colors.labelAlternative },

  section: { gap: space.s6 },
  sectionLabel: {
    fontSize: scale(12), fontWeight: '700', color: colors.labelAlternative,
    paddingHorizontal: space.s4,
  },
  menuCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, overflow: 'hidden', ...shadows.small,
  },

  row: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s16,
  },
  rowPressed: { backgroundColor: colors.fillNormal },
  rowLabel: { ...typography.body2n, color: colors.labelNormal },
  rowDanger: { color: colors.statusNegative },
  rowRight: { flexDirection: 'row', alignItems: 'center' },

  divider: { height: 1, backgroundColor: colors.line, marginHorizontal: space.s16 },
  versionTxt: { fontSize: scale(13), color: colors.labelAssistive },
});
