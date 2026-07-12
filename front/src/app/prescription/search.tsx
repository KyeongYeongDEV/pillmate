import React, { useState, useCallback, useEffect, useMemo, useRef } from 'react';
import {
  View, Text, FlatList, ScrollView, Pressable, StyleSheet,
  KeyboardAvoidingView, Platform, ActivityIndicator, Animated,
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
import { colors, space, radius, typography } from '@/styles/tokens';
import type { DrugSearchResult } from '@/types/prescription';
import { safeBack } from '@/lib/router/safeBack';
import { RECENT_SEARCHES_DISPLAY_COUNT } from '@/lib/constants';
import {
  getRecentSearches, saveRecentSearches, clearRecentSearches, pushRecentSearch,
} from '@/lib/search/recentSearches';

const MFDS_SOURCE = '식품의약품안전처 의약품안전나라';
const TOAST_DURATION_MS = 1800;

export default function DrugSearchScreen() {
  const dispatch = useAppDispatch();
  const existingItems = useAppSelector(s => s.prescriptionFlow.items);
  const { q: initialQ, mode } = useLocalSearchParams<{ q?: string; mode?: string }>();
  // mode=add(review "검색으로 추가")만 + 추가 가능. 그 외(기본, 등록 허브 "약 검색하기")는 순수 조회 전용.
  const isAddMode = mode === 'add';

  const addedKdCodes = useMemo(
    () => new Set(existingItems.map(i => i.kdCode).filter((k): k is string => k !== null)),
    [existingItems],
  );

  const [query, setQuery] = useState(initialQ ?? '');
  const [debouncedQuery, setDebouncedQuery] = useState(initialQ ?? '');
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const [toastVisible, setToastVisible] = useState(false);
  const [toastDrug, setToastDrug] = useState('');
  const toastOpacity = useRef(new Animated.Value(0)).current;
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    getRecentSearches().then(setRecentSearches);
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query.trim()), 300);
    return () => clearTimeout(timer);
  }, [query]);

  // 결과 카드와 실제로 상호작용(상세보기/추가)한 시점 = 유효 검색 확정 — 중간 타이핑은 기록 안 함
  const recordSearch = useCallback((term: string) => {
    if (!term.trim()) return;
    setRecentSearches(prev => {
      const next = pushRecentSearch(prev, term);
      saveRecentSearches(next);
      return next;
    });
  }, []);

  const { data: results = [], isFetching } = useSearchDrugsQuery(debouncedQuery, {
    skip: !debouncedQuery,
  });

  const showToast = useCallback((drugName: string) => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
    setToastDrug(drugName);
    setToastVisible(true);
    Animated.sequence([
      Animated.timing(toastOpacity, { toValue: 1, duration: 180, useNativeDriver: true }),
      Animated.delay(TOAST_DURATION_MS - 360),
      Animated.timing(toastOpacity, { toValue: 0, duration: 180, useNativeDriver: true }),
    ]).start();
    toastTimer.current = setTimeout(() => setToastVisible(false), TOAST_DURATION_MS);
  }, [toastOpacity]);

  const handleAdd = useCallback((item: DrugSearchResult) => {
    if (addedKdCodes.has(item.kdCode)) return;
    dispatch(addFromSearch({
      kdCode: item.kdCode,
      nameRaw: item.name,
      matchedName: item.name,
      imageUrl: item.imageUrl,
    }));
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    showToast(item.name);
    recordSearch(debouncedQuery);
    // Stay on search screen — user can add multiple drugs
  }, [dispatch, addedKdCodes, showToast, recordSearch, debouncedQuery]);

  const handleDetail = useCallback((item: DrugSearchResult) => {
    recordSearch(debouncedQuery);
    router.push(`/drug/${item.kdCode}`);
  }, [recordSearch, debouncedQuery]);

  const handleClear = useCallback(() => setQuery(''), []);

  const handleRecentSelect = useCallback((term: string) => {
    setQuery(term);
  }, []);

  const handleRecentRemove = useCallback((term: string) => {
    setRecentSearches(prev => {
      const next = prev.filter(r => r !== term);
      saveRecentSearches(next);
      return next;
    });
  }, []);

  const handleClearAllRecent = useCallback(() => {
    setRecentSearches([]);
    clearRecentSearches();
  }, []);

  const showResults = !!debouncedQuery;
  const displayedRecent = useMemo(
    () => recentSearches.slice(0, RECENT_SEARCHES_DISPLAY_COUNT),
    [recentSearches],
  );

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.topBar}>
        <DrugSearchBar
          value={query}
          onChangeText={setQuery}
          onClear={handleClear}
          autoFocus
        />
        <Pressable
          onPress={() => safeBack('/prescription')}
          accessibilityLabel="취소"
          accessibilityRole="button"
          style={styles.cancelBtn}
        >
          <Text style={styles.cancelTxt}>취소</Text>
        </Pressable>
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
                alreadyAdded={isAddMode && addedKdCodes.has(item.kdCode)}
                onAdd={isAddMode ? handleAdd : undefined}
                onDetail={handleDetail}
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
              items={displayedRecent}
              onSelect={handleRecentSelect}
              onRemove={handleRecentRemove}
              onClearAll={handleClearAllRecent}
            />
          </ScrollView>
        )}
      </KeyboardAvoidingView>

      {/* Toast — "처방전에 추가됨" */}
      {toastVisible && (
        <Animated.View style={[styles.toast, { opacity: toastOpacity }]} pointerEvents="none">
          <Text style={styles.toastTxt} numberOfLines={1}>
            ✓ 약봉투에 추가됨
          </Text>
        </Animated.View>
      )}
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
  toast: {
    position: 'absolute', bottom: space.s24, alignSelf: 'center',
    backgroundColor: colors.labelNormal, borderRadius: radius.r20,
    paddingHorizontal: space.s20, paddingVertical: space.s10,
  },
  toastTxt: { ...typography.label2, color: colors.bgNormal, fontWeight: '600' },
});
