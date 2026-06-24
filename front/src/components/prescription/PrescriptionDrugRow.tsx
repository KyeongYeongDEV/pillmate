import React, { memo, useCallback } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import type { PrescriptionDetailDrug } from '@/types/prescription';
import { scale, colors, space, radius } from '@/styles/tokens';

const CONFIDENCE_PERCENT = 100;

function PrescriptionDrugRow({ drug }: { drug: PrescriptionDetailDrug }) {
  const displayName = drug.matchedDrugName ?? drug.nameRaw;
  const subtitle = drug.matchedDrugName ? drug.nameRaw : null;

  const handlePress = useCallback(() => {
    if (drug.matchedKdCode) {
      router.push(`/drug/${drug.matchedKdCode}` as any);
    } else {
      router.push(`/drug/__unknown?name=${encodeURIComponent(drug.nameRaw)}` as any);
    }
  }, [drug.matchedKdCode, drug.nameRaw]);

  return (
    <Pressable
      style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
      onPress={handlePress}
      accessibilityLabel={`${displayName} 약 정보 보기`}
      accessibilityRole="button"
    >
      <View style={styles.headRow}>
        <Text style={styles.name} numberOfLines={1}>{displayName}</Text>
        <View style={styles.headRight}>
          {confidenceLabel(drug.confidence) && (
            <Text style={styles.confidence}>{confidenceLabel(drug.confidence)}</Text>
          )}
          <Feather name="chevron-right" size={scale(16)} color={colors.labelAlternative} />
        </View>
      </View>
      {subtitle ? <Text style={styles.rawName}>{subtitle}</Text> : null}
      <Text style={styles.dosage}>{dosageLine(drug)}</Text>
    </Pressable>
  );
}

function dosageLine(drug: PrescriptionDetailDrug): string {
  const dose = drug.doseAmount != null ? `${drug.doseAmount}${drug.doseUnit ?? ''}` : '용량 미상';
  const freq = drug.frequency != null ? `1일 ${drug.frequency}회` : '횟수 미상';
  const days = drug.durationDays != null ? `${drug.durationDays}일분` : '기간 미상';
  return `${dose} · ${freq} · ${days}`;
}

function confidenceLabel(confidence: number | null): string | null {
  if (confidence == null) return null;
  return `${Math.round(confidence * CONFIDENCE_PERCENT)}%`;
}

const styles = StyleSheet.create({
  row: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r12,
    borderWidth: 1, borderColor: colors.line, padding: space.s14, gap: space.s4,
  },
  rowPressed: { backgroundColor: colors.fillNormal },
  headRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space.s8 },
  headRight: { flexDirection: 'row', alignItems: 'center', gap: space.s4 },
  name: { flex: 1, fontSize: scale(15), fontWeight: '700', color: colors.labelNormal },
  confidence: { fontSize: scale(12), fontWeight: '600', color: colors.labelAlternative },
  rawName: { fontSize: scale(12), color: colors.labelAssistive },
  dosage: { fontSize: scale(13), color: colors.labelNeutral, marginTop: space.s4 },
});

export default memo(PrescriptionDrugRow);
