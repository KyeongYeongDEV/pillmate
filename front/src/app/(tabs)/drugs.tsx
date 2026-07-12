import React, { useState, useEffect } from 'react';
import {
  View, Text, FlatList, Pressable, StyleSheet, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { Image } from 'expo-image';
import DrugSearchBar from '@/components/search/DrugSearchBar';
import PillVisual from '@/components/common/PillVisual';
import { useSearchDrugsQuery } from '@/store/slices/drugApi';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import { MFDS_SOURCE } from '@/lib/constants';
import type { DrugSearchResult } from '@/types/prescription';

const SEARCH_DEBOUNCE_MS = 300;

export default function DrugsScreen() {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [query]);

  const { data: results = [], isFetching } = useSearchDrugsQuery(debouncedQuery, {
    skip: !debouncedQuery,
  });

  const showResults = !!debouncedQuery;

  return (
    <SafeAreaView style={styles.root} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.title}>약 검색</Text>
        <DrugSearchBar value={query} onChangeText={setQuery} onClear={() => setQuery('')} />
      </View>

      {showResults ? (
        <FlatList
          data={results}
          keyExtractor={item => item.kdCode}
          contentContainerStyle={styles.list}
          keyboardShouldPersistTaps="handled"
          renderItem={({ item }) => <DrugRow item={item} />}
          ListEmptyComponent={
            isFetching
              ? <ActivityIndicator style={styles.loader} color={colors.primaryBase} />
              : (
                <View style={styles.emptyBox}>
                  <Text style={styles.emptyText}>'{debouncedQuery}' 검색 결과가 없습니다.</Text>
                  <Text style={styles.emptySub}>다른 약 이름·성분으로 검색해보세요.</Text>
                </View>
              )
          }
          ListFooterComponent={
            results.length > 0 ? <Text style={styles.mfdsNote}>출처: {MFDS_SOURCE}</Text> : null
          }
        />
      ) : (
        <View style={styles.hint}>
          <Feather name="search" size={scale(28)} color={colors.labelAssistive} />
          <Text style={styles.hintText}>약 이름이나 성분으로 검색하세요</Text>
        </View>
      )}
    </SafeAreaView>
  );
}

function DrugRow({ item }: { item: DrugSearchResult }) {
  const [imgFailed, setImgFailed] = useState(false);
  const showImage = !!item.imageUrl && !imgFailed;

  return (
    <Pressable
      testID={`drug-row-${item.kdCode}`}
      style={styles.row}
      onPress={() => router.push(`/drug/${item.kdCode}`)}
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
      <View style={styles.rowInfo}>
        <Text style={styles.rowName} numberOfLines={1}>{item.name}</Text>
        <Text style={styles.rowSub} numberOfLines={1}>{item.ingredient ?? item.form ?? '—'}</Text>
      </View>
      <Feather name="chevron-right" size={scale(18)} color={colors.labelAssistive} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    paddingHorizontal: space.s16, paddingTop: space.s8, paddingBottom: space.s12,
    backgroundColor: colors.bgNormal, gap: space.s12,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  title: { ...typography.heading1, color: colors.labelNormal },
  list: { padding: space.s16, gap: space.s8 },
  row: {
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
    backgroundColor: colors.bgNormal, borderRadius: radius.r14, padding: space.s14,
    borderWidth: 1, borderColor: colors.line,
  },
  thumb: { width: scale(56), height: scale(42), borderRadius: radius.r8, backgroundColor: colors.bgAlt },
  rowInfo: { flex: 1, minWidth: 0 },
  rowName: { ...typography.body2n, color: colors.labelNormal, fontWeight: '700', letterSpacing: -0.012 },
  rowSub: { ...typography.caption1, color: colors.labelAlternative, marginTop: 1 },
  loader: { marginTop: space.s32 },
  emptyBox: { alignItems: 'center', paddingTop: space.s48, gap: space.s8 },
  emptyText: { ...typography.body2n, color: colors.labelNormal, fontWeight: '600' },
  emptySub: { ...typography.caption1, color: colors.labelAlternative },
  mfdsNote: { ...typography.caption1, color: colors.labelAssistive, textAlign: 'center', marginTop: space.s16 },
  hint: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space.s10 },
  hintText: { ...typography.body2r, color: colors.labelAlternative },
});
