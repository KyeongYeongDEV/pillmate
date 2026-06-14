import React from 'react';
import {
  View, Text, ScrollView, Pressable, StyleSheet, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { useGetDrugDetailQuery } from '@/store/slices/drugApi';
import DrugHero from '@/components/drug/DrugHero';
import DrugInfoSection from '@/components/drug/DrugInfoSection';
import SourceCard from '@/components/drug/SourceCard';
import { colors, space, typography } from '@/styles/tokens';
import { safeBack } from '@/lib/router/safeBack';

export default function DrugDetailScreen() {
  const { kdCode } = useLocalSearchParams<{ kdCode: string }>();
  const { data: drug, isLoading, isError } = useGetDrugDetailQuery(kdCode ?? '', { skip: !kdCode });

  if (isLoading) {
    return (
      <SafeAreaView style={styles.center}>
        <ActivityIndicator size="large" color={colors.primaryBase} />
      </SafeAreaView>
    );
  }

  if (isError || !drug) {
    return (
      <SafeAreaView style={styles.center}>
        <Text style={styles.errorText}>약 정보를 불러올 수 없습니다.</Text>
        <Pressable onPress={() => safeBack('/(tabs)/drugs')} style={styles.errorBack}>
          <Text style={styles.errorBackTxt}>뒤로 가기</Text>
        </Pressable>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Pressable
          onPress={() => safeBack('/(tabs)/drugs')}
          style={styles.backBtn}
          accessibilityLabel="뒤로 가기"
          accessibilityRole="button"
        >
          <Feather name="chevron-left" size={26} color={colors.labelNormal} />
        </Pressable>
        <Text style={styles.headerTitle}>약 정보</Text>
        <View style={styles.backBtn} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <DrugHero
          name={drug.name}
          company={drug.company}
          ingredient={drug.ingredient}
          form={drug.form}
          imageUrl={drug.imageUrl}
        />

        <DrugInfoSection title="효능·효과" text={drug.efficacy} />
        <DrugInfoSection title="용법·용량" text={drug.dosage} />
        <DrugInfoSection title="사용상의 주의사항" text={drug.sideEffect} />

        <SourceCard source={drug.source} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgNormal },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bgNormal },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s4, paddingVertical: space.s8,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  backBtn: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  headerTitle: { ...typography.headline2, color: colors.labelNormal, fontWeight: '700' },
  scroll: { paddingBottom: space.s40 },
  errorText: { ...typography.body2n, color: colors.labelAlternative, marginBottom: space.s16 },
  errorBack: { paddingHorizontal: space.s24, paddingVertical: space.s10 },
  errorBackTxt: { ...typography.label1n, color: colors.primaryBase },
});
