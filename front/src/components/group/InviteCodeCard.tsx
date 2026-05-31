import React, { useCallback } from 'react';
import { View, Text, StyleSheet, Pressable, Platform } from 'react-native';
import * as Clipboard from 'expo-clipboard';
import { colors, space, radius } from '@/styles/tokens';
import type { InviteCodeView } from '@/types/caregroup';

interface InviteCodeCardProps {
  inviteCode: InviteCodeView;
}

function InviteCodeCard({ inviteCode }: InviteCodeCardProps) {
  const handleCopy = useCallback(async () => {
    await Clipboard.setStringAsync(inviteCode.code);
  }, [inviteCode.code]);

  const expiryText = formatExpiry(inviteCode.expiresAt);

  return (
    <View style={styles.card}>
      <View style={styles.row}>
        <View>
          <Text style={styles.label}>초대 코드</Text>
          <Text style={styles.code}>{inviteCode.code}</Text>
        </View>
        <Pressable
          style={styles.copyBtn}
          onPress={handleCopy}
          accessibilityLabel="초대 코드 복사"
          accessibilityRole="button"
        >
          <Text style={styles.copyText}>복사</Text>
        </Pressable>
      </View>
      <Text style={styles.expiry}>{expiryText}</Text>
    </View>
  );
}

function formatExpiry(iso: string): string {
  const remaining = new Date(iso).getTime() - Date.now();
  if (remaining <= 0) return '만료됨';
  const mins = Math.floor(remaining / 60_000);
  if (mins < 60) return `유효 ${mins}분 · 가족에게 코드 또는 QR을 전송하세요.`;
  return `유효 ${Math.floor(mins / 60)}시간 · 가족에게 코드 또는 QR을 전송하세요.`;
}

export default React.memo(InviteCodeCard);

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16,
    padding: space.s18,
    borderWidth: 1,
    borderStyle: Platform.OS === 'ios' ? 'solid' : 'dashed',
    borderColor: colors.line,
    gap: space.s8,
  },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  label: { fontSize: 11, color: colors.labelAlternative, fontWeight: '600', letterSpacing: 0.04 },
  code: { fontSize: 22, fontWeight: '700', letterSpacing: 0.08, marginTop: 4, color: colors.labelNormal },
  copyBtn: {
    paddingHorizontal: space.s14, paddingVertical: space.s8,
    borderRadius: radius.full, backgroundColor: colors.fillNormal,
  },
  copyText: { fontSize: 13, fontWeight: '600', color: colors.labelNormal },
  expiry: { fontSize: 12, color: colors.labelAlternative, lineHeight: 17 },
});
