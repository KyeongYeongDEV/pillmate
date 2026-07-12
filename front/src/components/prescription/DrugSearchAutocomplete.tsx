import React, { memo, useCallback, useState, useRef } from 'react';
import {
  View, TextInput, FlatList, Pressable, Text, StyleSheet, ActivityIndicator,
} from 'react-native';
import { Image } from 'expo-image';
import { useLazySearchDrugsQuery } from '@/store/slices/drugApi';
import { DEBOUNCE_MS, MFDS_SOURCE } from '@/lib/constants';
import { scale, colors, typography, space, radius, shadows } from '@/styles/tokens';
import type { DrugSearchResult } from '@/types/prescription';

interface Props {
  onSelect: (drug: DrugSearchResult) => void;
}

function DrugSearchAutocomplete({ onSelect }: Props) {
  const [query, setQuery] = useState('');
  const [trigger, { data, isFetching }] = useLazySearchDrugsQuery();
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleChange = useCallback(
    (text: string) => {
      setQuery(text);
      if (debounceRef.current) clearTimeout(debounceRef.current);
      if (text.trim().length < 2) return;
      debounceRef.current = setTimeout(() => {
        trigger(text.trim());
      }, DEBOUNCE_MS);
    },
    [trigger],
  );

  const handleSelect = useCallback(
    (drug: DrugSearchResult) => {
      setQuery(drug.name);
      onSelect(drug);
    },
    [onSelect],
  );

  const renderItem = useCallback(
    ({ item }: { item: DrugSearchResult }) => (
      <Pressable
        style={styles.resultItem}
        onPress={() => handleSelect(item)}
        accessibilityLabel={`${item.name} 선택`}
        accessibilityRole="button"
      >
        {item.imageUrl ? (
          <Image
            source={{ uri: item.imageUrl }}
            style={styles.thumb}
            contentFit="contain"
            cachePolicy="memory-disk"
          />
        ) : (
          <View style={[styles.thumb, styles.thumbPlaceholder]}>
            <Text style={styles.pillEmoji}>💊</Text>
          </View>
        )}
        <View style={styles.resultText}>
          <Text style={styles.resultName} numberOfLines={1}>{item.name}</Text>
          {item.ingredient && <Text style={styles.resultCompany} numberOfLines={1}>{item.ingredient}</Text>}
          <Text style={styles.resultSource}>출처: {MFDS_SOURCE}</Text>
        </View>
      </Pressable>
    ),
    [handleSelect],
  );

  return (
    <View style={styles.container}>
      <View style={styles.inputWrapper}>
        <TextInput
          value={query}
          onChangeText={handleChange}
          placeholder="약 이름 검색..."
          placeholderTextColor={colors.labelAssistive}
          style={styles.input}
          accessibilityLabel="약 이름 검색"
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="search"
        />
        {isFetching && <ActivityIndicator size="small" color={colors.primaryNormal} style={styles.spinner} />}
      </View>
      {data && data.length > 0 && (
        <FlatList
          data={data}
          keyExtractor={(d) => d.kdCode}
          renderItem={renderItem}
          style={styles.results}
          keyboardShouldPersistTaps="handled"
          ItemSeparatorComponent={() => <View style={styles.separator} />}
        />
      )}
      {data && data.length === 0 && query.length >= 2 && !isFetching && (
        <View style={styles.empty}>
          <Text style={styles.emptyText}>검색 결과가 없어요. 직접 입력해 주세요.</Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: space.s4 },
  inputWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r12,
    borderWidth: 1.5,
    borderColor: colors.primaryNormal,
    paddingHorizontal: space.s12,
    height: scale(52),
  },
  input: { flex: 1, ...typography.body1n, color: colors.labelNormal },
  spinner: { marginLeft: space.s8 },
  results: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r12,
    borderWidth: 1,
    borderColor: colors.line,
    maxHeight: scale(240),
    ...shadows.small,
  },
  resultItem: { flexDirection: 'row', alignItems: 'center', padding: space.s12, gap: space.s10 },
  thumb: { width: scale(64), height: scale(48), borderRadius: radius.r8, backgroundColor: colors.bgAlt },
  thumbPlaceholder: { alignItems: 'center', justifyContent: 'center' },
  pillEmoji: { fontSize: scale(22) },
  resultText: { flex: 1 },
  resultName: { ...typography.body2n, color: colors.labelNormal, fontWeight: '600' },
  resultCompany: { ...typography.caption1, color: colors.labelAlternative, marginTop: 2 },
  resultSource: { ...typography.caption1, color: colors.labelAssistive, marginTop: 2 },
  separator: { height: scale(1), backgroundColor: colors.line, marginHorizontal: space.s12 },
  empty: { padding: space.s12 },
  emptyText: { ...typography.caption1, color: colors.labelAlternative, textAlign: 'center' },
});

export default memo(DrugSearchAutocomplete);
