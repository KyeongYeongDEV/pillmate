import React, { useState } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable, ActivityIndicator, Image,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { useGetPrescriptionDetailQuery } from '@/store/slices/prescriptionApi';
import OcrStatusChip from '@/components/prescription/OcrStatusChip';
import PrescriptionDrugRow from '@/components/prescription/PrescriptionDrugRow';
import { formatMonthDay } from '@/utils/calendarUtils';
import { safeBack } from '@/lib/router/safeBack';
import { scale, colors, space, radius, typography } from '@/styles/tokens';

export default function PrescriptionDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const prescriptionId = Number(id);
  const { data, isLoading, isError, refetch } = useGetPrescriptionDetailQuery(prescriptionId);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <Header />
      {renderBody()}
    </SafeAreaView>
  );

  function renderBody() {
    if (isLoading) return <ActivityIndicator size="large" color={colors.primaryBase} style={styles.loader} />;
    if (isError || !data) return <ErrorState onRetry={refetch} />;
    return (
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.metaRow}>
          <Text style={styles.date}>{formatMonthDay(data.prescribedAt)}</Text>
          <OcrStatusChip status={data.ocrStatus} />
        </View>
        <PrescriptionImage url={data.imageUrl} onRefresh={refetch} />
        <Text style={styles.sectionLabel}>약 {data.drugs.length}종</Text>
        <View style={styles.drugList}>
          {data.drugs.map((drug, i) => (
            <PrescriptionDrugRow key={`${drug.nameRaw}-${i}`} drug={drug} />
          ))}
          {data.drugs.length === 0 && <Text style={styles.empty}>등록된 약이 없습니다.</Text>}
        </View>
      </ScrollView>
    );
  }
}

function PrescriptionImage({ url, onRefresh }: { url: string | null; onRefresh: () => void }) {
  const [failed, setFailed] = useState(false);
  if (!url || failed) {
    return (
      <Pressable style={styles.imagePlaceholder} onPress={onRefresh} accessibilityLabel="처방전 이미지 다시 불러오기">
        <Feather name="image" size={scale(32)} color={colors.labelAssistive} />
        <Text style={styles.placeholderText}>
          {url ? '이미지를 불러올 수 없어요 · 탭하여 새로고침' : '등록된 이미지가 없어요'}
        </Text>
      </Pressable>
    );
  }
  return (
    <Image
      source={{ uri: url }}
      style={styles.image}
      resizeMode="cover"
      accessibilityLabel="처방전 이미지"
      onError={() => setFailed(true)}
    />
  );
}

function Header() {
  return (
    <View style={styles.header}>
      <Pressable
        onPress={() => safeBack('/(tabs)/prescriptions')}
        accessibilityLabel="뒤로가기"
        accessibilityRole="button"
        hitSlop={8}
      >
        <Feather name="chevron-left" size={scale(24)} color={colors.labelNormal} />
      </Pressable>
      <Text style={styles.headerTitle}>처방전 상세</Text>
      <View style={styles.headerSpacer} />
    </View>
  );
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <View style={styles.errorBox}>
      <Text style={styles.errorText}>처방전을 불러올 수 없어요</Text>
      <Pressable style={styles.retryBtn} onPress={onRetry} accessibilityLabel="다시 시도" accessibilityRole="button">
        <Text style={styles.retryText}>다시 시도</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal, borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  headerSpacer: { width: scale(24) },
  loader: { flex: 1, marginTop: space.s40 },
  content: { padding: space.s16, gap: space.s16, paddingBottom: 40 },
  metaRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  date: { fontSize: scale(18), fontWeight: '700', color: colors.labelNormal },
  image: { width: '100%', height: scale(220), borderRadius: radius.r16, backgroundColor: colors.fillNormal },
  imagePlaceholder: {
    width: '100%', height: scale(160), borderRadius: radius.r16, gap: space.s8,
    backgroundColor: colors.fillNormal, alignItems: 'center', justifyContent: 'center',
  },
  placeholderText: { fontSize: scale(13), color: colors.labelAlternative },
  sectionLabel: { fontSize: scale(13), fontWeight: '700', color: colors.labelAlternative },
  drugList: { gap: space.s8 },
  empty: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center', paddingVertical: space.s20 },
  errorBox: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space.s12, padding: space.s16 },
  errorText: { fontSize: scale(14), color: colors.labelAlternative },
  retryBtn: { paddingHorizontal: space.s20, paddingVertical: space.s12, borderRadius: radius.r12, backgroundColor: colors.primaryNormal },
  retryText: { fontSize: scale(14), fontWeight: '600', color: colors.staticWhite },
});
