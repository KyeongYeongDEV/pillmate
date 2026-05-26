import React, { useCallback } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { colors, typography, space, radius } from '@/styles/tokens';

export type InsightSeverity = 'INFO' | 'WARN' | 'CRITICAL';

interface InsightCardProps {
  severity: InsightSeverity;
  message: string;
  detail: string;
  onClose?: () => void;
  onDetail?: () => void;
}

const SEVERITY_COLORS: Record<InsightSeverity, { border: string; bg: string; icon: string }> = {
  INFO: { border: colors.primaryNormal, bg: '#EEF4FF', icon: colors.primaryNormal },
  WARN: { border: '#FF9200', bg: '#FFF8EE', icon: '#FF9200' },
  CRITICAL: { border: colors.statusNegative, bg: '#FFF0F0', icon: colors.statusNegative },
};

function InsightCard({ severity, message, detail, onClose, onDetail }: InsightCardProps) {
  // Fallback to WARN if severity is not recognized (e.g. unexpected API response)
  const theme = SEVERITY_COLORS[severity] ?? SEVERITY_COLORS.WARN;

  const handleClose = useCallback(() => onClose?.(), [onClose]);
  const handleDetail = useCallback(() => onDetail?.(), [onDetail]);

  return (
    <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.bg }]}>
      <View style={styles.header}>
        <View style={styles.titleRow}>
          <Feather name="zap" size={16} color={theme.icon} />
          <Text style={[styles.title, { color: theme.icon }]}>AI 분석</Text>
        </View>
        {onClose && (
          <Pressable
            onPress={handleClose}
            style={styles.closeBtn}
            accessibilityLabel="AI 분석 카드 닫기"
            accessibilityRole="button"
          >
            <Feather name="x" size={16} color={colors.labelAlternative} />
          </Pressable>
        )}
      </View>

      <Text style={styles.message}>{message}</Text>
      <Text style={styles.detail} numberOfLines={2}>{detail}</Text>

      {onDetail && (
        <Pressable onPress={handleDetail} accessibilityRole="button" accessibilityLabel="자세히 보기">
          <Text style={[styles.detailLink, { color: theme.icon }]}>자세히 보기 →</Text>
        </Pressable>
      )}
    </View>
  );
}

export default React.memo(InsightCard);

const styles = StyleSheet.create({
  card: {
    borderRadius: radius.r16,
    borderWidth: 1.5,
    padding: space.s16,
    gap: space.s8,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.s6,
  },
  title: {
    ...typography.label1n,
    fontWeight: '700',
  },
  closeBtn: {
    padding: space.s4,
  },
  message: {
    ...typography.body1n,
    color: colors.labelNormal,
    fontWeight: '600',
  },
  detail: {
    ...typography.body2r,
    color: colors.labelAlternative,
  },
  detailLink: {
    ...typography.label2,
    fontWeight: '600',
  },
});
