import React from 'react';
import {
  View, Text, ScrollView, Pressable, StyleSheet, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { useGetDrugDetailQuery } from '@/store/slices/drugApi';
import DrugHero from '@/components/drug/DrugHero';
import QuickStats from '@/components/drug/QuickStats';
import DetailTabs from '@/components/drug/DetailTabs';
import InteractionWarningCard from '@/components/drug/InteractionWarningCard';
import SideEffectChips from '@/components/drug/SideEffectChips';
import SourceCard from '@/components/drug/SourceCard';
import { colors, space, typography } from '@/styles/tokens';
import { safeBack } from '@/lib/router/safeBack';

export default function DrugDetailScreen() {
  const { kdCode } = useLocalSearchParams<{ kdCode: string }>();
  const { data: drug, isLoading, isError } = useGetDrugDetailQuery(kdCode ?? '');

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
      {/* Header */}
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
        {/* Share — Phase 2 */}
        <View style={styles.backBtn} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <DrugHero
          name={drug.name}
          englishName={drug.englishName}
          category={drug.category}
          company={drug.company}
        />

        <QuickStats />

        <DetailTabs
          efficacy={drug.efficacy}
          dosage={drug.dosage}
          warnings={drug.warnings}
        />

        <InteractionWarningCard interactions={drug.interactions} />

        <SideEffectChips sideEffects={drug.sideEffects} />

        <SourceCard source={drug.source} updatedAt={drug.updatedAt} />
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
