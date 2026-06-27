import React, { useState, useCallback } from 'react';
import {
  View, Text, TextInput, StyleSheet, Pressable, KeyboardAvoidingView, Platform,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import { scale, colors, space, radius, typography } from '@/styles/tokens';
import { useCreateGroupMutation } from '@/store/slices/caregroupApi';
import { safeBack } from '@/lib/router/safeBack';

const MAX_NAME_LEN = 30;

export default function CreateGroupScreen() {
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [createGroup, { isLoading: loading }] = useCreateGroupMutation();

  const canSubmit = name.trim().length > 0 && !loading;

  const handleCreate = useCallback(async () => {
    if (!canSubmit) return;
    setError(null);
    try {
      await createGroup({ name: name.trim() }).unwrap();
      safeBack('/(tabs)/group');
    } catch (e: any) {
      setError(e?.data?.error?.message ?? e?.message ?? '그룹 생성에 실패했어요');
    }
  }, [name, canSubmit, createGroup]);

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <Pressable onPress={() => safeBack('/(tabs)/group')} accessibilityLabel="뒤로가기" accessibilityRole="button" hitSlop={8}>
          <Feather name="x" size={scale(22)} color={colors.labelNormal} />
        </Pressable>
        <Text style={styles.headerTitle}>새 그룹 만들기</Text>
        <View style={{ width: scale(22) }} />
      </View>

      <KeyboardAvoidingView style={styles.body} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <Text style={styles.label}>그룹 이름</Text>
        <TextInput
          style={styles.input}
          placeholder="예: 할머니 댁, 우리 가족"
          placeholderTextColor={colors.labelAssistive}
          value={name}
          onChangeText={setName}
          maxLength={MAX_NAME_LEN}
          autoFocus
          accessibilityLabel="그룹 이름 입력"
        />
        <Text style={styles.charCount}>{name.length}/{MAX_NAME_LEN}</Text>

        {error && <Text style={styles.errorText}>{error}</Text>}

        <Pressable
          style={[styles.submitBtn, !canSubmit && styles.submitBtnDisabled]}
          onPress={handleCreate}
          disabled={!canSubmit}
          accessibilityLabel="그룹 만들기"
          accessibilityRole="button"
        >
          {loading
            ? <ActivityIndicator color={colors.staticWhite} />
            : <Text style={styles.submitText}>그룹 만들기</Text>}
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
  body: { flex: 1, padding: space.s20, gap: space.s8 },
  label: { fontSize: scale(14), fontWeight: '700', color: colors.labelNormal, marginBottom: space.s4 },
  input: {
    borderWidth: 1, borderColor: colors.line, borderRadius: radius.r12,
    paddingHorizontal: space.s16, paddingVertical: space.s14,
    fontSize: scale(16), color: colors.labelNormal, backgroundColor: colors.bgAlt,
  },
  charCount: { fontSize: scale(12), color: colors.labelAlternative, textAlign: 'right' },
  errorText: { fontSize: scale(13), color: colors.statusNegative },
  submitBtn: {
    marginTop: space.s16, height: scale(52), borderRadius: radius.r12,
    backgroundColor: colors.primaryBase, alignItems: 'center', justifyContent: 'center',
  },
  submitBtnDisabled: { backgroundColor: colors.fillStrong },
  submitText: { fontSize: scale(16), fontWeight: '700', color: colors.staticWhite },
});
