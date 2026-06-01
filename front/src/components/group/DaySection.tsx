import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors, space } from '@/styles/tokens';
import ActivityItemFull from '@/components/group/ActivityItemFull';
import type { ActivityView } from '@/types/caregroup';

interface DaySectionProps {
  title: string;
  items: ActivityView[];
  first?: boolean;
}

function DaySection({ title, items, first }: DaySectionProps) {
  if (items.length === 0) return null;
  return (
    <View style={[styles.section, first && styles.sectionFirst]}>
      <Text style={styles.title}>{title}</Text>
      <View style={styles.list}>
        {items.map((it, i) => (
          <ActivityItemFull
            key={`${it.occurredAt}-${i}`}
            item={it}
            last={i === items.length - 1}
          />
        ))}
      </View>
    </View>
  );
}

export default React.memo(DaySection);

const styles = StyleSheet.create({
  section: { paddingTop: space.s24 },
  sectionFirst: { paddingTop: space.s16 },
  title: {
    paddingHorizontal: space.s20,
    paddingBottom: space.s12,
    fontSize: 12, color: colors.labelAlternative,
    fontWeight: '700', letterSpacing: 0.48,
    textTransform: 'uppercase',
  },
  list: { paddingHorizontal: space.s20 },
});
