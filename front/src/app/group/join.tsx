import React, { useState, useCallback } from 'react';
import {
  View, Text, TextInput, StyleSheet, Pressable, KeyboardAvoidingView, Platform,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { colors, space, radius, typography } from '@/styles/tokens';
import { API_BASE_URL } from '@/lib/api/client';
import { getToken, getCurrentUserId } from '@/lib/auth/storage';
import type { ApiEnvelope } from '@/lib/api/client';

const INVITE_CODE_LEN = 6;

export default function JoinGroupScreen() {
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSubmit = code.trim().length === INVITE_CODE_LEN && !loading;

  const handleJoin = useCallback(async () => {
    if (!canSubmit) return;
    setLoading(true);
    setError(null);
    try {
      await joinGroup(code.trim().toUpperCase());
      router.back();
    } catch (e: any) {
      setError(e?.message ?? '그룹 참여에 실패했어요');
    } finally {
      setLoading(false);
    }
  }, [code, canSubmit]);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Pressable onPress={() => router.back()} accessibilityLabel="뒤로가기" accessibilityRole="button" hitSlop={8}>
          <Feather name="x" size={22} color={colors.labelNormal} />
        </Pressable>
        <Text style={styles.headerTitle}>초대 코드로 참여</Text>
        <View style={{ width: 22 }} />
      </View>

      <KeyboardAvoidingView style={styles.body} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <Text style={styles.desc}>가족에게 받은 6자리 초대 코드를 입력하세요</Text>

        <TextInput
          style={styles.input}
          placeholder="예: 3F9K2P"
          placeholderTextColor={colors.labelAssistive}
          value={code}
          onChangeText={(t) => setCode(t.toUpperCase())}
          maxLength={INVITE_CODE_LEN}
          autoCapitalize="characters"
          autoFocus
          accessibilityLabel="초대 코드 입력"
        />

        {error && <Text style={styles.errorText}>{error}</Text>}

        <Pressable
          style={[styles.submitBtn, !canSubmit && styles.submitBtnDisabled]}
          onPress={handleJoin}
          disabled={!canSubmit}
          accessibilityLabel="그룹 참여하기"
          accessibilityRole="button"
        >
          {loading
            ? <ActivityIndicator color={colors.staticWhite} />
            : <Text style={styles.submitText}>참여하기</Text>}
        </Pressable>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

async function joinGroup(code: string): Promise<void> {
  const token = await getToken();
  const userId = await getCurrentUserId();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (userId != null) headers['X-User-Id'] = String(userId);

  const res = await fetch(`${API_BASE_URL}/groups/join/${code}`, { method: 'POST', headers });
  const envelope: ApiEnvelope<unknown> = await res.json();
  if (!res.ok) {
    throw new Error(envelope?.error?.message ?? '초대 코드가 올바르지 않아요');
  }
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgNormal },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  body: { flex: 1, padding: space.s20, gap: space.s16 },
  desc: { fontSize: 15, color: colors.labelAlternative, lineHeight: 22 },
  input: {
    borderWidth: 1, borderColor: colors.line, borderRadius: radius.r12,
    paddingHorizontal: space.s16, paddingVertical: space.s14,
    fontSize: 24, fontWeight: '700', color: colors.labelNormal,
    backgroundColor: colors.bgAlt, letterSpacing: 4, textAlign: 'center',
  },
  errorText: { fontSize: 13, color: colors.statusNegative },
  submitBtn: {
    height: 52, borderRadius: radius.r12,
    backgroundColor: colors.primaryBase, alignItems: 'center', justifyContent: 'center',
  },
  submitBtnDisabled: { backgroundColor: colors.fillStrong },
  submitText: { fontSize: 16, fontWeight: '700', color: colors.staticWhite },
});
