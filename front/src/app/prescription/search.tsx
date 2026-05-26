import React, { useState, useCallback, useEffect, useMemo } from 'react';
import {
  View, Text, FlatList, ScrollView, Pressable, StyleSheet,
  KeyboardAvoidingView, Platform, Alert, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router, useLocalSearchParams } from 'expo-router';
import * as Haptics from 'expo-haptics';
import { useSearchDrugsQuery } from '@/store/slices/drugApi';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { addFromSearch } from '@/store/slices/prescriptionFlowSlice';
import DrugSearchBar from '@/components/search/DrugSearchBar';
import SearchResultCard from '@/components/search/SearchResultCard';
import RecentSearchChips from '@/components/search/RecentSearchChips';
import CategoryGrid from '@/components/search/CategoryGrid';
import { colors, space, radius, typography } from '@/styles/tokens';
import type { DrugSearchResult } from '@/types/prescription';

const MFDS_SOURCE = '식품의약품안전처 의약품안전나라';
const INITIAL_RECENT = ['메트포르민', '오메가-3', '글리메피리드'];

export default function DrugSearchScreen() {
  const dispatch = useAppDispatch();
  const existingItems = useAppSelector(s => s.prescriptionFlow.items);
  const { q: initialQ } = useLocalSearchParams<{ q?: string }>();

  const addedKdCodes = useMemo(
    () => new Set(existingItems.map(i => i.kdCode).filter((k): k is string => k !== null)),
    [existingItems],
  );

  const [query, setQuery] = useState(initialQ ?? '');
  const [debouncedQuery, setDebouncedQuery] = useState(initialQ ?? '');
  const [recentSearches, setRecentSearches] = useState<string[]>(INITIAL_RECENT);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query.trim()), 300);
    return () => clearTimeout(timer);
  }, [query]);

  const { data: results = [], isFetching } = useSearchDrugsQuery(debouncedQuery, {
    skip: !debouncedQuery,
  });

  const handleAdd = useCallback((item: DrugSearchResult) => {
    if (addedKdCodes.has(item.kdCode)) {
      Alert.alert('이미 추가된 약', `${item.name}은(는) 이미 처방전에 추가되어 있습니다.`);
      return;
    }
    dispatch(addFromSearch({
      kdCode: item.kdCode,
      nameRaw: item.name,
      matchedName: item.name,
      imageUrl: item.imageUrl,
    }));
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    router.back();
  }, [dispatch, addedKdCodes]);

  const handleClear = useCallback(() => setQuery(''), []);

  const handleCategorySelect = useCallback((category: string) => {
    setQuery(category);
  }, []);

  const handleRecentSelect = useCallback((term: string) => {
    setQuery(term);
  }, []);

  const handleRecentRemove = useCallback((term: string) => {
    setRecentSearches(prev => prev.filter(r => r !== term));
  }, []);

  const handleClearAllRecent = useCallback(() => setRecentSearches([]), []);

  const showResults = !!debouncedQuery;

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* 검색바 + 취소 */}
      <View style={styles.topBar}>
        <DrugSearchBar
          value={query}
          onChangeText={setQuery}
          onClear={handleClear}
          autoFocus
        />
        <Pressable
          onPress={() => router.back()}
          accessibilityLabel="취소"
          accessibilityRole="button"
          style={styles.cancelBtn}
        >
          <Text style={styles.cancelTxt}>취소</Text>
        </Pressable>
      </View>

      {/* 검색 모드 필터 */}
      <View style={styles.filterRow}>
        <View style={[styles.filterChip, styles.filterActive]}>
          <Text style={styles.filterActiveTxt}>✨ AI 의미 검색</Text>
        </View>
        {['이름', '성분', '효능'].map(f => (
          <View key={f} style={styles.filterChip}>
            <Text style={styles.filterTxt}>{f}</Text>
          </View>
        ))}
      </View>

      <KeyboardAvoidingView
        style={styles.kav}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        {showResults ? (
          <FlatList
            data={results}
            keyExtractor={item => item.kdCode}
            contentContainerStyle={styles.resultList}
            keyboardShouldPersistTaps="handled"
            ListHeaderComponent={
              <View style={styles.resultHeader}>
                <Text style={styles.resultCount}>
                  검색 결과 <Text style={styles.resultCountBold}>{results.length}건</Text>
                </Text>
              </View>
            }
            ListEmptyComponent={
              isFetching
                ? <ActivityIndicator style={styles.loader} color={colors.primaryBase} />
                : (
                  <View style={styles.emptyBox}>
                    <Text style={styles.emptyText}>'{debouncedQuery}' 검색 결과가 없습니다.</Text>
                    <Text style={styles.emptySub}>다른 약 이름 또는 성분으로 검색해보세요.</Text>
                  </View>
                )
            }
            ListFooterComponent={
              results.length > 0
                ? <Text style={styles.mfdsNote}>출처: {MFDS_SOURCE}</Text>
                : null
            }
            renderItem={({ item }) => (
              <SearchResultCard
                item={item}
                query={debouncedQuery}
                alreadyAdded={addedKdCodes.has(item.kdCode)}
                onAdd={handleAdd}
              />
            )}
            ItemSeparatorComponent={() => <View style={styles.separator} />}
          />
        ) : (
          <ScrollView
            contentContainerStyle={styles.emptyState}
            keyboardShouldPersistTaps="handled"
          >
            <RecentSearchChips
              items={recentSearches}
              onSelect={handleRecentSelect}
              onRemove={handleRecentRemove}
              onClearAll={handleClearAllRecent}
            />
            <CategoryGrid onSelect={handleCategorySelect} />

            {/* AI 힌트 카드 */}
            <View style={styles.aiHint}>
              <View style={styles.aiHintIcon}>
                <Text style={styles.aiHintIconTxt}>✨</Text>
              </View>
              <View style={styles.aiHintBody}>
                <Text style={styles.aiHintTitle}>약 이름이 기억나지 않으세요?</Text>
                <Text style={styles.aiHintSub}>
                  "흰색 동그란 알약, 혈압약" 처럼 설명해도 찾아드려요.
                </Text>
              </View>
            </View>
          </ScrollView>
        )}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgNormal },
  topBar: {
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
    paddingHorizontal: space.s16, paddingTop: space.s8, paddingBottom: space.s14,
    backgroundColor: colors.bgNormal,
  },
  cancelBtn: { paddingVertical: space.s4 },
  cancelTxt: { ...typography.body2n, color: colors.labelNormal, fontWeight: '600' },
  filterRow: {
    flexDirection: 'row', gap: space.s8,
    paddingHorizontal: space.s16, paddingBottom: space.s8,
  },
  filterChip: {
    paddingHorizontal: space.s12, paddingVertical: 7,
    borderRadius: radius.full, backgroundColor: colors.fillNormal,
  },
  filterActive: { backgroundColor: colors.violet95 },
  filterActiveTxt: { fontSize: 12, fontWeight: '700', color: colors.violet45 },
  filterTxt: { fontSize: 12, fontWeight: '600', color: colors.labelAlternative },
  kav: { flex: 1 },
  resultList: { padding: space.s16, paddingBottom: space.s48, gap: space.s8 },
  resultHeader: { marginBottom: space.s4 },
  resultCount: { ...typography.label2, color: colors.labelAlternative },
  resultCountBold: { ...typography.label2, color: colors.labelNormal, fontWeight: '700' },
  separator: { height: space.s8 },
  loader: { marginTop: space.s32 },
  emptyBox: { alignItems: 'center', paddingTop: space.s48, gap: space.s8 },
  emptyText: { ...typography.body2n, color: colors.labelNormal, fontWeight: '600' },
  emptySub: { ...typography.caption1, color: colors.labelAlternative },
  mfdsNote: {
    ...typography.caption1, color: colors.labelAssistive,
    textAlign: 'center', marginTop: space.s16,
  },
  emptyState: { paddingBottom: space.s48 },
  aiHint: {
    flexDirection: 'row', alignItems: 'flex-start', gap: space.s12,
    backgroundColor: colors.violet95, borderRadius: radius.r14, padding: space.s16,
    marginHorizontal: space.s16, marginTop: space.s24,
  },
  aiHintIcon: {
    width: 32, height: 32, borderRadius: radius.r10,
    backgroundColor: colors.bgNormal, alignItems: 'center', justifyContent: 'center',
    flexShrink: 0,
  },
  aiHintIconTxt: { fontSize: 16 },
  aiHintBody: { flex: 1 },
  aiHintTitle: { fontSize: 13, fontWeight: '700', color: colors.violet45, letterSpacing: -0.005 },
  aiHintSub: { fontSize: 12, color: colors.violet45, marginTop: 4, lineHeight: 18, opacity: 0.85 },
});
