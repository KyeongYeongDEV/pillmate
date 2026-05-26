import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import PillVisual from '@/components/common/PillVisual';
import { colors, space, radius, typography } from '@/styles/tokens';

interface DrugHeroProps {
  name: string;
  englishName: string | null;
  category: string | null;
  company: string | null;
}

export default function DrugHero({ name, englishName, category, company }: DrugHeroProps) {
  const sub = [englishName, company].filter(Boolean).join(' · ');
  return (
    <View style={styles.hero}>
      <PillVisual size={86} colorA="#a5c8f5" colorB="#d0e8ff" />
      {category && <Text style={styles.category}>{category}</Text>}
      <Text style={styles.name}>{name}</Text>
      {sub ? <Text style={styles.sub}>{sub}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  hero: {
    backgroundColor: colors.blue95, borderRadius: radius.r20,
    paddingVertical: space.s32, paddingHorizontal: space.s20,
    alignItems: 'center',
    marginHorizontal: space.s16, marginTop: space.s12, marginBottom: space.s24,
  },
  category: {
    fontSize: 11, fontWeight: '700', color: colors.primaryNormal,
    letterSpacing: 0.06, marginTop: space.s18,
    textTransform: 'uppercase',
  },
  name: {
    ...typography.heading1, color: colors.labelNormal,
    marginTop: space.s4, textAlign: 'center', letterSpacing: -0.018,
  },
  sub: {
    ...typography.label2, color: colors.labelAlternative,
    marginTop: space.s2, textAlign: 'center',
  },
});
