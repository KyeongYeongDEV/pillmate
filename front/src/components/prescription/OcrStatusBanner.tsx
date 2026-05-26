import React, { memo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { OcrStatus } from '@/types/prescription';
import { colors, typography, space, radius } from '@/styles/tokens';

interface Props {
  status: OcrStatus;
}

function OcrStatusBanner({ status }: Props) {
  if (status === 'DONE') return null;

  if (status === 'MANUAL') {
    return (
      <View style={styles.manual} accessibilityRole="alert" accessibilityLabel="수동 확인이 필요한 약이 있습니다">
        <Text style={styles.manualIcon}>⚠️</Text>
        <Text style={styles.manualText}>
          수동 확인이 필요한 약이 있습니다.{'\n'}
          <Text style={styles.manualBold}>약사·의사와 상담하세요.</Text>
        </Text>
      </View>
    );
  }

  if (status === 'FAILED') {
    return (
      <View style={styles.failed} accessibilityRole="alert" accessibilityLabel="처방전 인식에 실패했습니다">
        <Text style={styles.failedIcon}>❌</Text>
        <Text style={styles.failedText}>인식에 실패했습니다. 다시 시도하거나 직접 입력해주세요.</Text>
      </View>
    );
  }

  return null;
}

const styles = StyleSheet.create({
  manual: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space.s8,
    backgroundColor: '#FFFBE6',
    borderColor: colors.statusCautionary,
    borderWidth: 1,
    borderRadius: radius.r12,
    padding: space.s12,
    marginHorizontal: space.s16,
    marginBottom: space.s12,
  },
  manualIcon: { fontSize: 16 },
  manualText: { ...typography.caption1, color: colors.labelNeutral, flex: 1 },
  manualBold: { fontWeight: '700', color: colors.labelNormal },
  failed: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space.s8,
    backgroundColor: '#FFF0F0',
    borderColor: colors.statusNegative,
    borderWidth: 1,
    borderRadius: radius.r12,
    padding: space.s12,
    marginHorizontal: space.s16,
    marginBottom: space.s12,
  },
  failedIcon: { fontSize: 16 },
  failedText: { ...typography.caption1, color: colors.statusNegative, flex: 1 },
});

export default memo(OcrStatusBanner);
