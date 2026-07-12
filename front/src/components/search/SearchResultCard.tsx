import React, { memo, useState } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { Image } from 'expo-image';
import Highlight from './Highlight';
import PillVisual from '@/components/common/PillVisual';
import { scale, colors, space, radius, typography } from '@/styles/tokens';
import type { DrugSearchResult } from '@/types/prescription';

interface SearchResultCardProps {
  item: DrugSearchResult;
  query: string;
  alreadyAdded?: boolean;
  // 미전달 시 순수 조회 전용(+ 버튼 미노출) — 약봉투 등록 허브의 "약 검색하기" 등
  onAdd?: (item: DrugSearchResult) => void;
  onDetail: (item: DrugSearchResult) => void;
}

function SearchResultCard({ item, query, alreadyAdded, onAdd, onDetail }: SearchResultCardProps) {
  const [imgFailed, setImgFailed] = useState(false);
  const showImage = !!item.imageUrl && !imgFailed;

  return (
    <Pressable
      testID={`detail-btn-${item.kdCode}`}
      style={styles.card}
      onPress={() => onDetail(item)}
      accessibilityLabel={`${item.name} 상세 보기`}
      accessibilityRole="button"
    >
      {showImage ? (
        <Image
          source={{ uri: item.imageUrl! }}
          style={styles.thumb}
          contentFit="contain"
          cachePolicy="memory-disk"
          placeholder={{ blurhash: 'LEHV6nWB2yk8pyo0adR*.7kCMdnj' }}
          onError={() => setImgFailed(true)}
          accessibilityLabel="약 이미지"
        />
      ) : (
        <PillVisual size={scale(40)} colorA="#a5c8f5" colorB="#d0e8ff" />
      )}
      <View style={styles.info}>
        <View style={styles.nameRow}>
          <Highlight text={item.name} term={query} style={styles.name} />
          {onAdd && alreadyAdded && (
            <View style={styles.addedBadge}>
              <Text style={styles.addedBadgeText}>추가됨</Text>
            </View>
          )}
        </View>
        <Text style={styles.sub} numberOfLines={1}>{item.ingredient ?? item.form ?? '—'}</Text>
      </View>

      {/* info icon — visual indicator that card row = detail (not interactive, outer Pressable handles it) */}
      <View style={styles.infoIcon}>
        <Feather name="info" size={scale(14)} color={colors.labelAssistive} />
      </View>

      {onAdd && (
        <Pressable
          testID={`add-btn-${item.kdCode}`}
          style={[styles.addBtn, alreadyAdded && styles.addBtnDone]}
          onPress={() => onAdd(item)}
          disabled={alreadyAdded}
          accessibilityLabel={alreadyAdded ? '이미 추가됨' : `${item.name} 추가`}
          accessibilityRole="button"
        >
          <Feather
            name={alreadyAdded ? 'check' : 'plus'}
            size={scale(18)}
            color={alreadyAdded ? colors.statusPositive : colors.labelNormal}
          />
        </Pressable>
      )}
    </Pressable>
  );
}

export default memo(SearchResultCard);

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r14, padding: space.s14,
    borderWidth: 1, borderColor: colors.line,
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
  },
  thumb: { width: scale(56), height: scale(42), borderRadius: radius.r8, backgroundColor: colors.bgAlt },
  info: { flex: 1, minWidth: 0 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: space.s6, flexWrap: 'wrap' },
  name: { ...typography.body2n, color: colors.labelNormal, fontWeight: '700', letterSpacing: -0.012 },
  addedBadge: {
    paddingHorizontal: space.s6, paddingVertical: 2,
    borderRadius: radius.r4, backgroundColor: colors.blue95,
  },
  addedBadgeText: { fontSize: scale(10), fontWeight: '700', color: colors.primaryNormal, letterSpacing: 0.02 },
  sub: { ...typography.caption1, color: colors.labelAlternative, marginTop: 1 },
  infoIcon: { width: scale(20), height: scale(20), alignItems: 'center', justifyContent: 'center' },
  addBtn: {
    width: scale(32), height: scale(32), borderRadius: radius.r8,
    backgroundColor: colors.fillNormal,
    alignItems: 'center', justifyContent: 'center',
  },
  addBtnDone: { backgroundColor: colors.green95 },
});
