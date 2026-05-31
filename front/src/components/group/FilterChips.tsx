import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { colors, space, radius } from '@/styles/tokens';

export type GroupFilter = '전체' | '내가 환자' | '내가 보호자' | '비공개';

const FILTERS: GroupFilter[] = ['전체', '내가 환자', '내가 보호자', '비공개'];

interface FilterChipsProps {
  selected: GroupFilter;
  onSelect: (filter: GroupFilter) => void;
}

function FilterChips({ selected, onSelect }: FilterChipsProps) {
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.container}
    >
      {FILTERS.map((f) => {
        const active = f === selected;
        return (
          <Pressable
            key={f}
            style={[styles.chip, active && styles.chipActive]}
            onPress={() => onSelect(f)}
            accessibilityLabel={`${f} 필터`}
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
          >
            <Text style={[styles.chipText, active && styles.chipTextActive]}>{f}</Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

export default React.memo(FilterChips);

const styles = StyleSheet.create({
  container: { flexDirection: 'row', gap: space.s8, paddingHorizontal: space.s16 },
  chip: {
    paddingHorizontal: space.s14,
    paddingVertical: space.s6,
    borderRadius: radius.full,
    backgroundColor: colors.bgNormal,
    borderWidth: 1,
    borderColor: colors.line,
  },
  chipActive: {
    backgroundColor: colors.primaryBase,
    borderColor: colors.primaryBase,
  },
  chipText: { fontSize: 13, fontWeight: '600', color: colors.labelAlternative },
  chipTextActive: { color: '#fff' },
});
