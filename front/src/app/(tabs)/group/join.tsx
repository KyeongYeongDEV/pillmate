import React, { useState, useCallback } from 'react';
import {
  View, Text, TextInput, StyleSheet, Pressable, KeyboardAvoidingView, Platform,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { scale, colors, space, radius, typography } from '@/styles/tokens';
import { useJoinGroupMutation } from '@/store/slices/caregroupApi';
import { joinGroupErrorMessage } from '@/lib/caregroup/joinError';
import { safeBack } from '@/lib/router/safeBack';

const INVITE_CODE_LEN = 6;

export default function JoinGroupScreen() {
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [joinGroup, { isLoading: loading }] = useJoinGroupMutation();

  const canSubmit = code.trim().length === INVITE_CODE_LEN && !loading;

  const handleJoin = useCallback(async () => {
    if (!canSubmit) return;
    setError(null);
    try {
      await joinGroup(code.trim().toUpperCase()).unwrap();
      safeBack('/(tabs)/group');
    } catch (e) {
      setError(joinGroupErrorMessage(e));
    }
  }, [code, canSubmit, joinGroup]);

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <Pressable onPress={() => safeBack('/(tabs)/group')} accessibilityLabel="뒤로가기" accessibilityRole="button" hitSlop={8}>
          <Feather name="x" size={scale(22)} color={colors.labelNormal} />
        </Pressable>
        <Text style={styles.headerTitle}>초대 코드로 참여</Text>
        <Pressable
          onPress={() => router.replace('/group/scan' as any)}
          accessibilityLabel="QR 스캔으로 참여"
          accessibilityRole="button"
          hitSlop={8}
        >
          <Feather name="maximize" size={scale(22)} color={colors.labelNormal} />
        </Pressable>
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

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgNormal },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  body: { flex: 1, padding: space.s20, gap: space.s16 },
  desc: { fontSize: scale(15), color: colors.labelAlternative, lineHeight: scale(22) },
  input: {
    borderWidth: 1, borderColor: colors.line, borderRadius: radius.r12,
    paddingHorizontal: space.s16, paddingVertical: space.s14,
    fontSize: scale(24), fontWeight: '700', color: colors.labelNormal,
    backgroundColor: colors.bgAlt, letterSpacing: 4, textAlign: 'center',
  },
  errorText: { fontSize: scale(13), color: colors.statusNegative },
  submitBtn: {
    height: scale(52), borderRadius: radius.r12,
    backgroundColor: colors.primaryBase, alignItems: 'center', justifyContent: 'center',
  },
  submitBtnDisabled: { backgroundColor: colors.fillStrong },
  submitText: { fontSize: scale(16), fontWeight: '700', color: colors.staticWhite },
});
