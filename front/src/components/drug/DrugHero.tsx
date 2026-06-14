import React from 'react';
import { View, Text, Image, StyleSheet } from 'react-native';
import PillVisual from '@/components/common/PillVisual';
import { colors, space, radius, typography } from '@/styles/tokens';

interface DrugHeroProps {
  name: string;
  company: string | null;
  ingredient: string | null;
  form: string | null;
  imageUrl: string | null;
}

export default function DrugHero({ name, company, ingredient, form, imageUrl }: DrugHeroProps) {
  const meta = [form, company].filter(Boolean).join(' · ');
  return (
    <View style={styles.hero}>
      {imageUrl
        ? <Image source={{ uri: imageUrl }} style={styles.image} resizeMode="contain" />
        : <PillVisual size={86} colorA="#a5c8f5" colorB="#d0e8ff" />}
      <Text style={styles.name}>{name}</Text>
      {meta ? <Text style={styles.sub}>{meta}</Text> : null}
      {ingredient ? <Text style={styles.ingredient}>{ingredient}</Text> : null}
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
  image: { width: 96, height: 96, borderRadius: radius.r16, backgroundColor: colors.bgNormal },
  name: {
    ...typography.heading1, color: colors.labelNormal,
    marginTop: space.s14, textAlign: 'center', letterSpacing: -0.018,
  },
  sub: {
    ...typography.label2, color: colors.labelAlternative,
    marginTop: space.s4, textAlign: 'center',
  },
  ingredient: {
    ...typography.caption1, color: colors.labelAlternative,
    marginTop: space.s2, textAlign: 'center',
  },
});
