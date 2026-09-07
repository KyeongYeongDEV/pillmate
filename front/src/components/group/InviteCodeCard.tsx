import React, { useCallback } from 'react';
import { View, Text, StyleSheet, Pressable, Share } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import { scale, colors, space, radius, fontFamily } from '@/styles/tokens';
import { useCountdown } from '@/hooks/useCountdown';
import type { InviteCodeView } from '@/types/caregroup';

const QR_SIZE = 132;

function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

interface InviteCodeCardProps {
  inviteCode: InviteCodeView | null | undefined;
  onExpire?: () => void;
}

function InviteCodeCard({ inviteCode, onExpire }: InviteCodeCardProps) {
  const { remainingSeconds } = useCountdown(inviteCode?.expiresAt ?? null, onExpire);

  const handleCopy = useCallback(async () => {
    if (!inviteCode) return;
    await Share.share({ message: inviteCode.code, title: 'PillMate 초대 코드' });
  }, [inviteCode]);

  if (!inviteCode) {
    return null;
  }

  const expiryText = remainingSeconds > 0
    ? `유효 ${formatCountdown(remainingSeconds)} · 가족에게 코드 또는 QR을 전송하세요.`
    : '만료됨';

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
      <View style={styles.qrBox} accessibilityLabel="초대 코드 QR" accessibilityRole="image">
        <QRCode
          value={inviteCode.code}
          size={QR_SIZE}
          color={colors.labelNormal}
          backgroundColor={colors.bgNormal}
        />
      </View>
      <Text style={styles.expiry}>{expiryText}</Text>
    </View>
  );
}

export default React.memo(InviteCodeCard);

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16,
    padding: space.s18,
    borderWidth: 1,
    borderStyle: 'solid',
    borderColor: colors.line,
    gap: space.s8,
  },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  label: { fontSize: scale(11), color: colors.labelAlternative, fontWeight: '600', letterSpacing: 0.04 },
  code: {
    fontSize: scale(22), fontWeight: '700', letterSpacing: 1.76, marginTop: 4,
    color: colors.labelNormal, fontFamily: fontFamily.mono,
  },
  copyBtn: {
    paddingHorizontal: space.s14, paddingVertical: space.s8,
    borderRadius: radius.full, backgroundColor: colors.fillNormal,
  },
  copyText: { fontSize: scale(13), fontWeight: '600', color: colors.labelNormal },
  qrBox: {
    alignSelf: 'center',
    padding: space.s10,
    borderRadius: radius.r12,
    backgroundColor: colors.bgNormal,
    marginTop: space.s4,
  },
  expiry: { fontSize: scale(12), color: colors.labelAlternative, lineHeight: scale(17), textAlign: 'center' },
});
