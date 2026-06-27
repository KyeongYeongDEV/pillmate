import React, { useCallback } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import Icon from '@/components/common/Icon';
import { scale, colors, typography, space, radius } from '@/styles/tokens';

export type InsightSeverity = 'INFO' | 'WARN' | 'CRITICAL';

// 카드마다 반복되던 면책 문구 제거 — 상세 화면은 섹션 footer로 1회만 노출
const DISCLAIMER_RE = /\s*참고용입니다\.\s*약사[·.]?의사와\s*상담하세요\.?\s*$/g;

export function stripInsightDisclaimer(text: string): string {
  return text.replace(DISCLAIMER_RE, '').trimEnd();
}

interface InsightCardProps {
  severity: InsightSeverity;
  message: string;
  detail: string;
  subtitle?: string;
  onClose?: () => void;
  onDetail?: () => void;
}

// severity prop kept for API compatibility; visual is uniform per design
function InsightCard({ message, detail, subtitle, onClose, onDetail }: InsightCardProps) {
  const handleClose = useCallback(() => onClose?.(), [onClose]);
  const handleDetail = useCallback(() => onDetail?.(), [onDetail]);

  return (
    <View style={styles.card}>
      {/* Left: sparkle icon in violet box */}
      <View style={styles.iconBox}>
        <Icon name="sparkles" size={scale(20)} color={colors.violet45} />
      </View>

      {/* Right: content */}
      <View style={styles.content}>
        <Text style={styles.message}>{message}</Text>
        {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
        <Text style={styles.detail}>{stripInsightDisclaimer(detail)}</Text>

        <View style={styles.actions}>
          {onDetail && (
            <Pressable
              onPress={handleDetail}
              style={styles.btnPrimary}
              accessibilityRole="button"
              accessibilityLabel="자세히 보기"
            >
              <Text style={styles.btnPrimaryText}>알림 조정</Text>
            </Pressable>
          )}
          {onClose && (
            <Pressable
              onPress={handleClose}
              style={styles.btnSecondary}
              accessibilityRole="button"
              accessibilityLabel="AI 분석 카드 닫기"
            >
              <Text style={styles.btnSecondaryText}>나중에</Text>
            </Pressable>
          )}
        </View>
      </View>
    </View>
  );
}

export default React.memo(InsightCard);

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space.s14,
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16,
    borderWidth: 1,
    borderColor: colors.line,
    padding: space.s18,
  },
  iconBox: {
    width: scale(36),
    height: scale(36),
    borderRadius: radius.r10,
    backgroundColor: colors.violet95,
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: {
    flex: 1,
    gap: space.s6,
  },
  message: {
    fontSize: scale(15),
    fontWeight: '700',
    color: colors.labelNormal,
    lineHeight: scale(22),
  },
  subtitle: {
    ...typography.caption1,
    color: colors.labelAlternative,
  },
  detail: {
    fontSize: scale(13),
    fontWeight: '500',
    color: colors.labelNeutral,
    lineHeight: scale(20),
  },
  actions: {
    flexDirection: 'row',
    gap: space.s6,
    marginTop: space.s6,
  },
  btnPrimary: {
    backgroundColor: colors.labelNormal,
    borderRadius: radius.r8,
    paddingVertical: space.s6,
    paddingHorizontal: space.s12,
  },
  btnPrimaryText: {
    fontSize: scale(13),
    fontWeight: '600',
    color: '#fff',
  },
  btnSecondary: {
    backgroundColor: colors.fillNormal,
    borderRadius: radius.r8,
    paddingVertical: space.s6,
    paddingHorizontal: space.s12,
  },
  btnSecondaryText: {
    fontSize: scale(13),
    fontWeight: '600',
    color: colors.labelNeutral,
  },
});
