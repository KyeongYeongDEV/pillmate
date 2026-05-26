import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { colors, space, radius, typography } from '@/styles/tokens';

type Category = { name: string; icon: string; tint: string; fg: string };

const CATEGORIES: Category[] = [
  { name: '고혈압',    icon: '心', tint: '#fff0f0', fg: '#e02020' },
  { name: '당뇨',     icon: '糖', tint: colors.orange95, fg: colors.orange40 },
  { name: '콜레스테롤', icon: '脂', tint: colors.violet95, fg: colors.violet45 },
  { name: '소화제',   icon: '胃', tint: colors.green95, fg: colors.green40 },
  { name: '진통제',   icon: '痛', tint: colors.blue95,  fg: colors.primaryNormal },
  { name: '감기',     icon: '冒', tint: '#e0f9fd', fg: '#007a91' },
  { name: '기타',     icon: '其', tint: '#1a1a1a', fg: '#ffffff' },
];

interface CategoryGridProps {
  onSelect: (category: string) => void;
}

export default function CategoryGrid({ onSelect }: CategoryGridProps) {
  const topRows: Category[][] = [CATEGORIES.slice(0, 3), CATEGORIES.slice(3, 6)];
  const extra = CATEGORIES.slice(6);
  return (
    <View style={styles.section}>
      <Text style={styles.label}>카테고리로 찾기</Text>
      <View style={styles.grid}>
        {topRows.map((row, ri) => (
          <View key={ri} style={styles.row}>
            {row.map(cat => (
              <Pressable
                key={cat.name}
                style={styles.cell}
                onPress={() => onSelect(cat.name)}
                accessibilityLabel={`${cat.name} 카테고리 검색`}
                accessibilityRole="button"
              >
                <View style={[styles.iconBox, { backgroundColor: cat.tint }]}>
                  <Text style={[styles.catIcon, { color: cat.fg }]}>{cat.icon}</Text>
                </View>
                <Text style={styles.catName}>{cat.name}</Text>
              </Pressable>
            ))}
          </View>
        ))}
        {extra.length > 0 && (
          <View style={styles.row}>
            {extra.map(cat => (
              <Pressable
                key={cat.name}
                style={[styles.cell, styles.cellWide]}
                onPress={() => onSelect(cat.name)}
                accessibilityLabel={`${cat.name} 카테고리 검색`}
                accessibilityRole="button"
              >
                <View style={[styles.iconBox, { backgroundColor: cat.tint }]}>
                  <Text style={[styles.catIcon, { color: cat.fg }]}>{cat.icon}</Text>
                </View>
                <Text style={[styles.catName, { color: cat.fg }]}>{cat.name}</Text>
              </Pressable>
            ))}
          </View>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  section: { paddingTop: space.s24, paddingHorizontal: space.s16 },
  label: {
    fontSize: 13, fontWeight: '700', color: colors.labelAlternative,
    letterSpacing: 0.04, textTransform: 'uppercase', marginBottom: space.s10,
  },
  grid: { gap: space.s8 },
  row: { flexDirection: 'row', gap: space.s8 },
  cell: {
    flex: 1, backgroundColor: colors.bgNormal, borderWidth: 1, borderColor: colors.line,
    borderRadius: radius.r14, paddingVertical: space.s16, paddingHorizontal: space.s12,
    alignItems: 'center', gap: space.s8,
  },
  iconBox: {
    width: 40, height: 40, borderRadius: radius.r10,
    alignItems: 'center', justifyContent: 'center',
  },
  catIcon: { fontSize: 18, fontWeight: '700' },
  catName: { ...typography.label2, color: colors.labelNormal, fontWeight: '600', textAlign: 'center' },
  cellWide: { flex: 0, width: '100%', flexDirection: 'row', justifyContent: 'center', gap: space.s10 },
});
