import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView } from 'react-native';
import { scale, colors, space, radius } from '@/styles/tokens';

export type GroupFilter = '전체' | '내가 환자' | '내가 보호자' | '비공개';

const FILTERS: GroupFilter[] = ['전체', '내가 환자', '내가 보호자', '비공개'];

interface FilterChipsProps {
  selected: GroupFilter;
  onSelect: (filter: GroupFilter) => void;
}

function FilterChips({ selected, onSelect }: FilterChipsProps) {
  return (
    <View style={styles.wrapper}>
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
    </View>
  );
}

export default React.memo(FilterChips);

const CHIP_HEIGHT = 36;

const styles = StyleSheet.create({
  wrapper: { height: CHIP_HEIGHT, marginVertical: space.s4 },
  container: {
    flexDirection: 'row',
    gap: space.s6,
    paddingHorizontal: space.s20,
    alignItems: 'center',
  },
  chip: {
    height: CHIP_HEIGHT,
    paddingHorizontal: space.s12,
    borderRadius: radius.full,
    backgroundColor: colors.fillNormal,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
  },
  chipActive: {
    backgroundColor: colors.labelNormal,
  },
  chipText: {
    fontSize: scale(12), fontWeight: '500', color: colors.labelAlternative,
    lineHeight: scale(16), includeFontPadding: false,
  },
  chipTextActive: { color: colors.staticWhite, fontWeight: '700' },
});
