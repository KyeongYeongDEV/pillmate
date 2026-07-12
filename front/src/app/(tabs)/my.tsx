import React, { useEffect, useState, useCallback } from 'react';
import {
  View, Text, Pressable, ScrollView, StyleSheet, Alert, Linking, Modal, TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import Constants from 'expo-constants';
import { clearAuth, getDisplayName, saveDisplayName } from '@/lib/auth/storage';
import { useUpdateUserNameMutation } from '@/store/slices/userApi';
import { colors, space, scale, radius, typography, shadows } from '@/styles/tokens';
import { NAME_MIN_LENGTH, NAME_MAX_LENGTH } from '@/lib/constants';

const TERMS_URL = 'https://pillmate.app/terms';
const PRIVACY_URL = 'https://pillmate.app/privacy';
const APP_VERSION = Constants.expoConfig?.version ?? '1.0.0';
const DEFAULT_PROFILE_NAME = '내 계정';

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
        style={({ pressed }) => pressed && styles.rowPressed}
        onPress={onPress}
        disabled={!onPress}
        accessibilityRole={onPress ? 'button' : 'text'}
        accessibilityLabel={label}
      >
        <View style={styles.row}>
          <Text
            style={[styles.rowLabel, danger && styles.rowDanger]}
            numberOfLines={1}
            ellipsizeMode="tail"
          >
            {label}
          </Text>
          <View style={styles.rowRight}>
            {right ?? (
              onPress && <Feather name="chevron-right" size={scale(18)} color={colors.labelAssistive} />
            )}
          </View>
        </View>
      </Pressable>
    </>
  );
}

// ── Screen ────────────────────────────────────────────────────────────────────

export default function MyScreen() {
  const [name, setName] = useState<string | null>(null);
  const [editVisible, setEditVisible] = useState(false);
  const [nameInput, setNameInput] = useState('');
  const [updateUserName, { isLoading: isUpdatingName }] = useUpdateUserNameMutation();

  useEffect(() => {
    getDisplayName().then(stored => { if (stored) setName(stored); });
  }, []);

  const handleOpenEdit = useCallback(() => {
    setNameInput(name ?? '');
    setEditVisible(true);
  }, [name]);

  const handleCancelEdit = useCallback(() => {
    setEditVisible(false);
  }, []);

  const handleConfirmEdit = useCallback(async () => {
    const trimmed = nameInput.trim();
    if (trimmed.length < NAME_MIN_LENGTH || trimmed.length > NAME_MAX_LENGTH) {
      Alert.alert('닉네임 확인', `닉네임은 ${NAME_MIN_LENGTH}~${NAME_MAX_LENGTH}자로 입력해주세요.`);
      return;
    }
    try {
      const profile = await updateUserName({ name: trimmed }).unwrap();
      await saveDisplayName(profile.name);
      setName(profile.name);
      setEditVisible(false);
    } catch {
      Alert.alert('오류', '닉네임 변경에 실패했어요. 다시 시도해주세요.');
    }
  }, [nameInput, updateUserName]);

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
        {/* Profile card — 로그인 시 저장된 닉네임 표시(없으면 기본 문구), 편집 가능 */}
        <View style={styles.profileCard}>
          <View style={styles.avatar}>
            <Feather name="user" size={scale(26)} color={colors.primaryNormal} />
          </View>
          <View style={styles.profileInfo}>
            <View style={styles.profileNameRow}>
              <Text style={styles.profileName} numberOfLines={1} ellipsizeMode="tail">
                {name ?? DEFAULT_PROFILE_NAME}
              </Text>
              <Pressable
                onPress={handleOpenEdit}
                accessibilityLabel="닉네임 변경"
                accessibilityRole="button"
                hitSlop={8}
              >
                <Feather name="edit-2" size={scale(15)} color={colors.labelAlternative} />
              </Pressable>
            </View>
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

      {/* 닉네임 변경 — 이름만 간단 입력 */}
      <Modal visible={editVisible} transparent animationType="fade" onRequestClose={handleCancelEdit}>
        <Pressable style={styles.editBackdrop} onPress={handleCancelEdit}>
          <Pressable style={styles.editCard} onPress={() => {}}>
            <Text style={styles.editTitle}>닉네임 변경</Text>
            <TextInput
              style={styles.editInput}
              placeholder="닉네임을 입력해주세요"
              placeholderTextColor={colors.labelAssistive}
              value={nameInput}
              onChangeText={setNameInput}
              maxLength={NAME_MAX_LENGTH}
              autoFocus
              returnKeyType="done"
              onSubmitEditing={handleConfirmEdit}
            />
            <View style={styles.editBtnRow}>
              <Pressable
                style={styles.editCancelBtn}
                onPress={handleCancelEdit}
                accessibilityLabel="취소"
                accessibilityRole="button"
              >
                <Text style={styles.editCancelTxt}>취소</Text>
              </Pressable>
              <Pressable
                style={[styles.editConfirmBtn, (!nameInput.trim() || isUpdatingName) && styles.editConfirmDisabled]}
                onPress={handleConfirmEdit}
                disabled={!nameInput.trim() || isUpdatingName}
                accessibilityLabel="확인"
                accessibilityRole="button"
              >
                <Text style={styles.editConfirmTxt}>{isUpdatingName ? '변경 중…' : '확인'}</Text>
              </Pressable>
            </View>
          </Pressable>
        </Pressable>
      </Modal>
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
  profileInfo: { gap: space.s2, flex: 1, minWidth: 0 },
  profileNameRow: { flexDirection: 'row', alignItems: 'center', gap: space.s6 },
  profileName: { ...typography.headline1, color: colors.labelNormal, flexShrink: 1 },
  profileSub: { ...typography.label2, color: colors.labelAlternative },

  section: { gap: space.s6 },
  sectionLabel: {
    fontSize: scale(13), fontWeight: '700', color: colors.labelAlternative,
    paddingHorizontal: space.s4,
  },
  menuCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, overflow: 'hidden', ...shadows.small,
  },

  row: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s16,
    minHeight: scale(56), width: '100%',
  },
  rowPressed: { backgroundColor: colors.fillNormal },
  rowLabel: { ...typography.body1n, color: colors.labelNormal, flex: 1, marginRight: space.s8 },
  rowDanger: { color: colors.statusNegative },
  rowRight: { flexDirection: 'row', alignItems: 'center', flexShrink: 0, marginLeft: 'auto' },

  divider: { height: 1, backgroundColor: colors.line, marginHorizontal: space.s16 },
  versionTxt: { ...typography.label1n, color: colors.labelAssistive },

  editBackdrop: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.55)',
    alignItems: 'center', justifyContent: 'center', padding: space.s24,
  },
  editCard: {
    width: '100%', backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    padding: space.s20, gap: space.s16, ...shadows.medium,
  },
  editTitle: { ...typography.headline2, color: colors.labelNormal },
  editInput: {
    ...typography.body1n, color: colors.labelNormal,
    paddingHorizontal: space.s16, paddingVertical: space.s12, minHeight: scale(48),
    borderRadius: radius.r12, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.bgAlt,
  },
  editBtnRow: { flexDirection: 'row', gap: space.s10 },
  editCancelBtn: {
    flex: 1, paddingVertical: space.s14, borderRadius: radius.r12,
    borderWidth: 1, borderColor: colors.line, alignItems: 'center',
  },
  editCancelTxt: { ...typography.label2, color: colors.labelAlternative, fontWeight: '700' },
  editConfirmBtn: {
    flex: 1, paddingVertical: space.s14, borderRadius: radius.r12,
    backgroundColor: colors.primaryNormal, alignItems: 'center',
  },
  editConfirmDisabled: { opacity: 0.5 },
  editConfirmTxt: { ...typography.label2, color: '#fff', fontWeight: '700' },
});
