import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { colors, space, radius, typography } from '@/styles/tokens';

interface RecentSearchChipsProps {
  items: string[];
  onSelect: (term: string) => void;
  onRemove: (term: string) => void;
  onClearAll: () => void;
}

export default function RecentSearchChips({ items, onSelect, onRemove, onClearAll }: RecentSearchChipsProps) {
  if (items.length === 0) return null;
  return (
    <View style={styles.section}>
      <View style={styles.header}>
        <Text style={styles.label}>최근 검색</Text>
        <Pressable onPress={onClearAll} accessibilityLabel="최근 검색 전체 삭제" accessibilityRole="button">
          <Text style={styles.clearAll}>전체 삭제</Text>
        </Pressable>
      </View>
      <View style={styles.chips}>
        {items.map(r => (
          <View key={r} style={styles.chip}>
            <Feather name="clock" size={13} color={colors.labelAlternative} />
            <Pressable onPress={() => onSelect(r)} accessibilityLabel={`${r} 검색`} accessibilityRole="button">
              <Text style={styles.chipText}>{r}</Text>
            </Pressable>
            <Pressable
              onPress={() => onRemove(r)}
              style={styles.removeBtn}
              accessibilityLabel={`${r} 삭제`}
              accessibilityRole="button"
            >
              <Feather name="x" size={9} color="#fff" />
            </Pressable>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  section: { paddingTop: space.s20, paddingHorizontal: space.s16 },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    marginBottom: space.s10,
  },
  label: {
    fontSize: 13, fontWeight: '700', color: colors.labelAlternative,
    letterSpacing: 0.04, textTransform: 'uppercase',
  },
  clearAll: { ...typography.caption1, color: colors.labelAlternative },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space.s6 },
  chip: {
    flexDirection: 'row', alignItems: 'center', gap: space.s6,
    paddingHorizontal: space.s12, paddingVertical: space.s8,
    borderRadius: radius.full, backgroundColor: colors.bgNormal,
    borderWidth: 1, borderColor: colors.line,
  },
  chipText: { ...typography.label2, color: colors.labelNormal, fontWeight: '500' },
  removeBtn: {
    width: 14, height: 14, borderRadius: 7,
    backgroundColor: colors.labelAssistive,
    alignItems: 'center', justifyContent: 'center',
    marginLeft: 2,
  },
});
