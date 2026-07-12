import React, { memo, useCallback, useMemo } from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { scale, colors, typography, space, radius, shadows } from '@/styles/tokens';
import { OCR_MIN_CONFIDENCE, MFDS_SOURCE } from '@/lib/constants';
import type { DrugListItem } from '@/types/prescription';

interface Props {
  item: DrugListItem;
  onRemove: (id: string) => void;
}

function DrugCard({ item, onRemove }: Props) {
  const handleRemove = useCallback(() => onRemove(item.id), [item.id, onRemove]);

  const { borderColor, isLowConf, isUnmatched } = useMemo(() => {
    const unmatched = item.kdCode === null && item.source === 'OCR_AUTO';
    const lowConf = item.confidence !== null && item.confidence < OCR_MIN_CONFIDENCE;
    return {
      isUnmatched: unmatched,
      isLowConf: lowConf,
      borderColor: unmatched
        ? colors.statusCautionary
        : lowConf
          ? '#ff9200'
          : colors.line,
    };
  }, [item.kdCode, item.source, item.confidence]);

  const sourceLabel = item.source === 'MANUAL_INPUT'
    ? `출처: 사용자 입력 (${MFDS_SOURCE} 미검증)`
    : `출처: ${MFDS_SOURCE}`;

  return (
    <View
      style={[styles.card, { borderColor }]}
      accessibilityLabel={`약 카드: ${item.matchedName ?? item.nameRaw}`}
    >
      {/* 상단 행 */}
      <View style={styles.topRow}>
        {item.imageUrl ? (
          <Image
            source={{ uri: item.imageUrl }}
            style={styles.thumbnail}
            contentFit="contain"
            cachePolicy="memory-disk"
            placeholder={{ blurhash: 'LEHV6nWB2yk8pyo0adR*.7kCMdnj' }}
            accessibilityLabel="약 이미지"
          />
        ) : (
          <View style={[styles.thumbnail, styles.thumbnailPlaceholder]}>
            <Text style={styles.pillEmoji}>💊</Text>
          </View>
        )}
        <View style={styles.nameBlock}>
          <Text style={styles.name} numberOfLines={2}>{item.matchedName ?? item.nameRaw}</Text>
          {item.matchedName && item.matchedName !== item.nameRaw && (
            <Text style={styles.nameRaw} numberOfLines={1}>{item.nameRaw}</Text>
          )}
          <Text style={styles.source}>{sourceLabel}</Text>
        </View>
        <View style={styles.rightCol}>
          {isLowConf && (
            <View style={styles.warnBadge} accessibilityLabel="신뢰도 낮음">
              <Text style={styles.warnIcon}>⚠️</Text>
            </View>
          )}
          {item.confidence !== null && (
            <Text style={[styles.conf, isLowConf && styles.confLow]}>
              {Math.round(item.confidence * 100)}%
            </Text>
          )}
        </View>
      </View>

      {/* 삭제 */}
      <Pressable onPress={handleRemove} style={styles.removeBtn} accessibilityLabel="약 삭제" accessibilityRole="button">
        <Text style={styles.removeTxt}>삭제</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16,
    borderWidth: 1,
    padding: space.s16,
    gap: space.s12,
    ...shadows.small,
  },
  topRow: { flexDirection: 'row', alignItems: 'flex-start', gap: space.s12 },
  thumbnail: { width: scale(72), height: scale(56), borderRadius: radius.r8, backgroundColor: colors.bgAlt },
  thumbnailPlaceholder: { alignItems: 'center', justifyContent: 'center' },
  pillEmoji: { fontSize: scale(28) },
  nameBlock: { flex: 1 },
  name: { ...typography.body1n, color: colors.labelNormal },
  nameRaw: { ...typography.caption1, color: colors.labelAlternative, marginTop: 2 },
  source: { ...typography.caption1, color: colors.labelAssistive, marginTop: 4 },
  rightCol: { alignItems: 'flex-end', gap: space.s4 },
  warnBadge: {
    width: scale(28), height: scale(28), borderRadius: radius.r8,
    backgroundColor: '#FFF3E0', alignItems: 'center', justifyContent: 'center',
  },
  warnIcon: { fontSize: scale(14) },
  conf: { ...typography.caption1, color: colors.statusPositive, fontWeight: '700' },
  confLow: { color: colors.statusNegative },
  removeBtn: { alignSelf: 'flex-end' },
  removeTxt: { ...typography.label2, color: colors.statusNegative },
});

export default memo(DrugCard);
