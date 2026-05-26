import React, { memo } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import Highlight from './Highlight';
import PillVisual from '@/components/common/PillVisual';
import { colors, space, radius, typography } from '@/styles/tokens';
import type { DrugSearchResult } from '@/types/prescription';

interface SearchResultCardProps {
  item: DrugSearchResult;
  query: string;
  alreadyAdded: boolean;
  onAdd: (item: DrugSearchResult) => void;
}

function SearchResultCard({ item, query, alreadyAdded, onAdd }: SearchResultCardProps) {
  return (
    <View style={styles.card}>
      <PillVisual size={40} colorA="#a5c8f5" colorB="#d0e8ff" />
      <View style={styles.info}>
        <View style={styles.nameRow}>
          <Highlight text={item.name} term={query} style={styles.name} />
          {alreadyAdded && (
            <View style={styles.addedBadge}>
              <Text style={styles.addedBadgeText}>추가됨</Text>
            </View>
          )}
        </View>
        <Text style={styles.sub} numberOfLines={1}>{item.company ?? '—'}</Text>
      </View>
      <Pressable
        style={[styles.addBtn, alreadyAdded && styles.addBtnDone]}
        onPress={() => onAdd(item)}
        disabled={alreadyAdded}
        accessibilityLabel={alreadyAdded ? '이미 추가됨' : `${item.name} 추가`}
        accessibilityRole="button"
      >
        <Feather
          name={alreadyAdded ? 'check' : 'plus'}
          size={18}
          color={alreadyAdded ? colors.statusPositive : colors.labelNormal}
        />
      </Pressable>
    </View>
  );
}

export default memo(SearchResultCard);

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r14, padding: space.s14,
    borderWidth: 1, borderColor: colors.line,
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
  },
  info: { flex: 1, minWidth: 0 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: space.s6, flexWrap: 'wrap' },
  name: { ...typography.body2n, color: colors.labelNormal, fontWeight: '700', letterSpacing: -0.012 },
  addedBadge: {
    paddingHorizontal: space.s6, paddingVertical: 2,
    borderRadius: radius.r4, backgroundColor: colors.blue95,
  },
  addedBadgeText: { fontSize: 10, fontWeight: '700', color: colors.primaryNormal, letterSpacing: 0.02 },
  sub: { ...typography.caption1, color: colors.labelAlternative, marginTop: 1 },
  addBtn: {
    width: 32, height: 32, borderRadius: radius.r8,
    backgroundColor: colors.fillNormal,
    alignItems: 'center', justifyContent: 'center',
  },
  addBtnDone: { backgroundColor: colors.green95 },
});
