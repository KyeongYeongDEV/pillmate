import React, { useCallback } from 'react';
import {
  View, Text, Pressable, ScrollView, StyleSheet, Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { colors, typography, space, radius, shadows } from '@/styles/tokens';
import { usePrescriptionReview } from '@/hooks/usePrescriptionReview';
import { useLogAliasMutation } from '@/store/slices/prescriptionApi';
import { useAppDispatch } from '@/store/hooks';
import { reset as resetFlow } from '@/store/slices/prescriptionFlowSlice';
import DrugMatchCard from '@/components/prescription/DrugMatchCard';
import DrugSearchModal from '@/components/prescription/DrugSearchModal';
import DDIWarningCard from '@/components/prescription/DDIWarningCard';

export default function PrescriptionConfirmScreen() {
  const {
    items, prescriptionId, warnings, aliasLogs,
    replaceTargetId, addModalVisible,
    hasLowConfidenceItems,
    openReplace, closeReplace, confirmReplace,
    handleRemove,
    openAdd, closeAdd, confirmAdd,
  } = usePrescriptionReview();

  const [logAlias] = useLogAliasMutation();
  const dispatch = useAppDispatch();

  const handleCancel = useCallback(() => {
    router.back();
  }, []);

  const handleRetake = useCallback(() => {
    dispatch(resetFlow());
    router.replace('/prescription/camera' as any);
  }, [dispatch]);

  const handleConfirm = useCallback(async () => {
    if (items.length === 0) {
      Alert.alert('약이 없어요', '등록할 약이 없습니다. 약을 추가하거나 취소해 주세요.');
      return;
    }

    if (hasLowConfidenceItems) {
      Alert.alert(
        '낮은 신뢰도 약 포함',
        '신뢰도가 낮거나 확인되지 않은 약이 있어요. 그대로 등록할까요?\n\n약사·의사와 상담을 권장합니다.',
        [
          { text: '다시 확인', style: 'cancel' },
          { text: '등록', onPress: () => submitAndNavigate() },
        ],
      );
      return;
    }

    submitAndNavigate();
  }, [items, hasLowConfidenceItems]);

  const submitAndNavigate = useCallback(async () => {
    // alias 로그 — fire-and-forget (MVP: 로깅만, admin review 대기)
    for (const log of aliasLogs) {
      logAlias(log).catch(() => {});
    }

    if (prescriptionId != null) {
      router.replace(`/prescription/result/${prescriptionId}` as any);
    } else {
      router.replace('/(tabs)/prescriptions' as any);
    }
  }, [aliasLogs, prescriptionId, logAlias]);

  const replaceTarget = replaceTargetId
    ? items.find(i => i.id === replaceTargetId)
    : null;

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <Pressable
          onPress={handleCancel}
          style={styles.headerBtn}
          accessibilityLabel="닫기"
          accessibilityRole="button"
        >
          <Text style={styles.headerBtnTxt}>✕</Text>
        </Pressable>
        <Text style={styles.headerTitle}>처방전 확인</Text>
        <View style={styles.headerBtn} />
      </View>

      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {/* 약 목록 헤더 */}
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>📋 인식된 약 ({items.length}건)</Text>
          {hasLowConfidenceItems && (
            <View style={styles.reviewBadge}>
              <Text style={styles.reviewBadgeTxt}>확인 필요</Text>
            </View>
          )}
        </View>

        {/* 약 카드 목록 */}
        {items.length === 0 ? (
          <View style={styles.emptyBox}>
            <Text style={styles.emptyTxt}>인식된 약이 없어요.</Text>
          </View>
        ) : (
          items.map(item => (
            <DrugMatchCard
              key={item.id}
              item={item}
              onReplace={openReplace}
              onRemove={handleRemove}
            />
          ))
        )}

        {/* 누락 약 추가 */}
        <Pressable
          onPress={openAdd}
          style={styles.addBtn}
          accessibilityLabel="누락된 약 추가하기"
          accessibilityRole="button"
        >
          <Text style={styles.addBtnTxt}>+ 누락된 약 추가하기</Text>
        </Pressable>

        {/* DDI 경고 */}
        {warnings.length > 0 && (
          <View style={styles.ddiSection}>
            <Text style={styles.sectionTitle}>⚠️ 약물 상호작용 주의 ({warnings.length}건)</Text>
            {warnings.map((w, i) => (
              <DDIWarningCard key={i} warning={w} />
            ))}
          </View>
        )}

        {/* 의료 안전 안내 */}
        <View style={styles.safetyCard}>
          <Text style={styles.safetyText}>
            ⚠️ 정확하지 않은 약은 [수정] 또는 [삭제] 후 등록해 주세요.{'\n'}
            출처: 식품의약품안전처. 약사·의사 상담을 권장합니다.
          </Text>
        </View>
      </ScrollView>

      {/* 하단 버튼 */}
      <View style={styles.footer}>
        <Pressable
          onPress={handleRetake}
          style={styles.retakeBtn}
          accessibilityLabel="다시 찍기"
          accessibilityRole="button"
        >
          <Text style={styles.retakeTxt}>📷 다시 찍기</Text>
        </Pressable>
        <Pressable
          onPress={handleCancel}
          style={styles.cancelBtn}
          accessibilityLabel="취소"
          accessibilityRole="button"
        >
          <Text style={styles.cancelTxt}>취소</Text>
        </Pressable>
        <Pressable
          onPress={handleConfirm}
          style={[styles.confirmBtn, hasLowConfidenceItems && styles.confirmBtnWarn]}
          accessibilityLabel="확인 후 등록"
          accessibilityRole="button"
        >
          <Text style={styles.confirmTxt}>
            {hasLowConfidenceItems ? '⚠️ 확인' : '확인 후 등록'}
          </Text>
        </Pressable>
      </View>

      {/* 수정 Modal */}
      <DrugSearchModal
        visible={replaceTargetId !== null}
        title={`"${replaceTarget?.matchedName ?? replaceTarget?.nameRaw ?? '약'}" 수정`}
        onClose={closeReplace}
        onSelect={confirmReplace}
      />

      {/* 추가 Modal */}
      <DrugSearchModal
        visible={addModalVisible}
        title="약 추가하기"
        onClose={closeAdd}
        onSelect={confirmAdd}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerBtn: { width: 40, alignItems: 'center', justifyContent: 'center' },
  headerBtnTxt: { fontSize: 18, color: colors.labelNormal },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  scroll: { padding: space.s16, gap: space.s12, paddingBottom: space.s24 },
  sectionHeader: {
    flexDirection: 'row', alignItems: 'center', gap: space.s8,
    marginBottom: space.s4,
  },
  sectionTitle: { ...typography.label1n, color: colors.labelNeutral, fontWeight: '700' },
  reviewBadge: {
    paddingHorizontal: space.s8, paddingVertical: 2,
    borderRadius: radius.full,
    backgroundColor: colors.red95,
  },
  reviewBadgeTxt: { ...typography.caption1, color: colors.red40, fontWeight: '700' },
  emptyBox: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r12,
    padding: space.s24, alignItems: 'center',
    borderWidth: 1, borderColor: colors.line,
  },
  emptyTxt: { ...typography.body2r, color: colors.labelAlternative },
  addBtn: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r12,
    padding: space.s14,
    borderWidth: 1.5, borderColor: colors.primaryBase, borderStyle: 'dashed',
    ...shadows.small,
  },
  addBtnTxt: { ...typography.body2n, color: colors.primaryBase, fontWeight: '700' },
  ddiSection: { gap: space.s8 },
  safetyCard: {
    backgroundColor: colors.orange95,
    borderRadius: radius.r12,
    padding: space.s14,
    borderWidth: 1, borderColor: colors.orange40,
  },
  safetyText: { ...typography.caption1, color: colors.orange40, lineHeight: 18 },
  footer: {
    flexDirection: 'row', gap: space.s10,
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal,
    borderTopWidth: 1, borderTopColor: colors.line,
  },
  retakeBtn: {
    flex: 1, paddingVertical: space.s14,
    borderRadius: radius.r12,
    backgroundColor: colors.bgAlt,
    borderWidth: 1, borderColor: colors.line,
    alignItems: 'center',
  },
  retakeTxt: { fontSize: 12, color: colors.labelNormal, fontWeight: '600', textAlign: 'center' },
  cancelBtn: {
    flex: 1, paddingVertical: space.s14,
    borderRadius: radius.r12,
    backgroundColor: colors.bgAlt,
    borderWidth: 1, borderColor: colors.line,
    alignItems: 'center',
  },
  cancelTxt: { ...typography.body1n, color: colors.labelNormal, fontWeight: '600' },
  confirmBtn: {
    flex: 2, paddingVertical: space.s14,
    borderRadius: radius.r12,
    backgroundColor: colors.primaryNormal,
    alignItems: 'center',
    ...shadows.small,
  },
  confirmBtnWarn: { backgroundColor: colors.orange40 },
  confirmTxt: { ...typography.body1n, color: '#fff', fontWeight: '700' },
});
