import React, { memo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { PrescriptionDetailDrug } from '@/types/prescription';
import { scale, colors, space, radius } from '@/styles/tokens';

const CONFIDENCE_PERCENT = 100;

function PrescriptionDrugRow({ drug }: { drug: PrescriptionDetailDrug }) {
  return (
    <View style={styles.row}>
      <View style={styles.headRow}>
        <Text style={styles.name} numberOfLines={1}>{drug.matchedDrugName ?? drug.nameRaw}</Text>
        {confidenceLabel(drug.confidence) && (
          <Text style={styles.confidence}>{confidenceLabel(drug.confidence)}</Text>
        )}
      </View>
      <Text style={styles.matched}>
        {drug.matchedDrugName ? `원문: ${drug.nameRaw}` : '미매칭'}
      </Text>
      <Text style={styles.dosage}>{dosageLine(drug)}</Text>
    </View>
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
  headRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space.s8 },
  name: { flex: 1, fontSize: scale(15), fontWeight: '700', color: colors.labelNormal },
  confidence: { fontSize: scale(12), fontWeight: '600', color: colors.labelAlternative },
  matched: { fontSize: scale(12), color: colors.labelAssistive },
  dosage: { fontSize: scale(13), color: colors.labelNeutral, marginTop: space.s4 },
});

export default memo(PrescriptionDrugRow);
