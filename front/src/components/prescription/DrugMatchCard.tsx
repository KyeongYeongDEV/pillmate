import React, { memo, useMemo } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { Image } from 'expo-image';
import { scale, colors, typography, space, radius, shadows } from '@/styles/tokens';
import { MFDS_SOURCE } from '@/lib/constants';
import type { DrugListItem } from '@/types/prescription';

const CONF_HIGH = 0.8;
const CONF_MED = 0.5;

interface ConfidenceTier {
  label: string;
  icon: string;
  fg: string;
  bg: string;
}

function getConfidenceTier(confidence: number | null, kdCode: string | null): ConfidenceTier | null {
  if (kdCode === null) return { label: '인식 실패', icon: '❌', fg: colors.red40, bg: colors.red95 };
  if (confidence === null) return { label: '검증됨', icon: '✅', fg: colors.green30, bg: colors.green95 };
  if (confidence >= CONF_HIGH) return { label: `높음 ${Math.round(confidence * 100)}%`, icon: '✅', fg: colors.green30, bg: colors.green95 };
  if (confidence >= CONF_MED) return { label: `보통 ${Math.round(confidence * 100)}%`, icon: '⚠️', fg: colors.orange40, bg: colors.orange95 };
  return { label: `낮음 ${Math.round(confidence * 100)}%`, icon: '⚠️', fg: colors.red40, bg: colors.red95 };
}

interface Props {
  item: DrugListItem;
  onReplace: (id: string) => void;
  onRemove: (id: string) => void;
}

function DrugMatchCard({ item, onReplace, onRemove }: Props) {
  const tier = useMemo(
    () => getConfidenceTier(item.confidence, item.kdCode),
    [item.confidence, item.kdCode],
  );

  const isLow = item.confidence !== null && item.confidence < CONF_MED;
  const isUnmatched = item.kdCode === null;
  const borderColor = isUnmatched || isLow ? colors.red40 : colors.line;

  return (
    <View
      style={[styles.card, { borderColor }]}
      accessibilityLabel={`${item.matchedName ?? item.nameRaw}, 신뢰도 ${tier?.label}`}
    >
      {/* 상단 행 */}
      <View style={styles.topRow}>
        {item.imageUrl ? (
          <Image
            source={{ uri: item.imageUrl }}
            style={styles.thumb}
            contentFit="contain"
            cachePolicy="memory-disk"
            placeholder={{ blurhash: 'LEHV6nWB2yk8pyo0adR*.7kCMdnj' }}
            accessibilityLabel="약 이미지"
          />
        ) : (
          <View style={[styles.thumb, styles.thumbPlaceholder]}>
            <Text style={styles.pillEmoji}>💊</Text>
          </View>
        )}

        <View style={styles.nameBlock}>
          <Text style={styles.name} numberOfLines={2}>
            {item.matchedName ?? item.nameRaw}
          </Text>
          {item.matchedName && item.matchedName !== item.nameRaw && (
            <Text style={styles.nameRaw} numberOfLines={1}>"{item.nameRaw}"</Text>
          )}
          <Text style={styles.source}>출처: {MFDS_SOURCE}</Text>
        </View>

        {tier && (
          <View style={[styles.tierBadge, { backgroundColor: tier.bg }]}>
            <Text style={styles.tierIcon}>{tier.icon}</Text>
            <Text style={[styles.tierLabel, { color: tier.fg }]}>{tier.label}</Text>
          </View>
        )}
      </View>

      {/* 낮음/인식실패 경고 배너 */}
      {(isLow || isUnmatched) && (
        <View style={styles.warnBanner}>
          <Text style={styles.warnText}>
            {isUnmatched
              ? '식약처 DB에서 자동 확인되지 않았어요. [다른 약 검색]으로 직접 지정하거나 삭제해 주세요.'
              : '매칭 신뢰도가 낮아요. 약이 맞는지 확인해 주세요. 약사·의사와 상담 권장.'}
          </Text>
        </View>
      )}

      {/* 액션 버튼 */}
      <View style={styles.actions}>
        <Pressable
          onPress={() => onReplace(item.id)}
          style={styles.actionBtn}
          accessibilityLabel="다른 약으로 수정"
          accessibilityRole="button"
        >
          <Text style={styles.actionBtnTxt}>수정</Text>
        </Pressable>
        <Pressable
          onPress={() => onRemove(item.id)}
          style={[styles.actionBtn, styles.actionBtnDanger]}
          accessibilityLabel="약 삭제"
          accessibilityRole="button"
        >
          <Text style={[styles.actionBtnTxt, styles.actionBtnDangerTxt]}>삭제</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16,
    borderWidth: 1.5,
    padding: space.s14,
    gap: space.s10,
    ...shadows.small,
  },
  topRow: { flexDirection: 'row', alignItems: 'flex-start', gap: space.s12 },
  thumb: { width: scale(52), height: scale(52), borderRadius: radius.r8, backgroundColor: colors.bgAlt },
  thumbPlaceholder: { alignItems: 'center', justifyContent: 'center' },
  pillEmoji: { fontSize: scale(24) },
  nameBlock: { flex: 1 },
  name: { ...typography.body1n, color: colors.labelNormal, fontWeight: '600' },
  nameRaw: { ...typography.caption1, color: colors.labelAlternative, marginTop: 2 },
  source: { ...typography.caption1, color: colors.labelAssistive, marginTop: 4 },
  tierBadge: {
    borderRadius: radius.r8,
    paddingHorizontal: space.s8,
    paddingVertical: space.s4,
    alignItems: 'center',
    gap: 2,
    minWidth: scale(64),
  },
  tierIcon: { fontSize: scale(14) },
  tierLabel: { ...typography.caption1, fontWeight: '700', textAlign: 'center' },
  warnBanner: {
    backgroundColor: colors.red95,
    borderRadius: radius.r8,
    padding: space.s10,
  },
  warnText: { ...typography.caption1, color: colors.red40, lineHeight: scale(18) },
  actions: { flexDirection: 'row', gap: space.s8, justifyContent: 'flex-end' },
  actionBtn: {
    paddingHorizontal: space.s14,
    paddingVertical: space.s6,
    borderRadius: radius.r8,
    backgroundColor: colors.bgAlt,
    borderWidth: 1,
    borderColor: colors.line,
  },
  actionBtnTxt: { ...typography.label2, color: colors.labelNormal, fontWeight: '600' },
  actionBtnDanger: { backgroundColor: colors.red95, borderColor: colors.red40 },
  actionBtnDangerTxt: { color: colors.red40 },
});

export default memo(DrugMatchCard);
