import React, { useCallback, useMemo } from 'react';
import {
  View, Text, Pressable, ScrollView, FlatList, TextInput,
  StyleSheet, Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { scale, colors, typography, space, radius, shadows } from '@/styles/tokens';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { removeItem, setMemo } from '@/store/slices/prescriptionFlowSlice';
import { createSelector } from 'reselect';
import type { RootState } from '@/store';
import DrugCard from '@/components/prescription/DrugCard';
import OcrStatusBanner from '@/components/prescription/OcrStatusBanner';
import DDIWarningCard from '@/components/prescription/DDIWarningCard';
import { safeBack } from '@/lib/router/safeBack';

// selector 메모이제이션 — 불필요 리렌더 방지
const selectFlow = createSelector(
  (state: RootState) => state.prescriptionFlow,
  (flow) => flow,
);

export default function ResultScreen() {
  const dispatch = useAppDispatch();
  const { items, ocrStatus, memo, warnings } = useAppSelector(selectFlow);
  const criticalCount = useMemo(() => warnings.filter(w => w.severity === 'CRITICAL').length, [warnings]);

  const handleRemove = useCallback(
    (id: string) => dispatch(removeItem(id)),
    [dispatch],
  );

  const handleAddManual = useCallback(() => {
    router.push('/prescription/manual' as any);
  }, []);

  const averageConfidence = useMemo(() => {
    const matched = items.filter(i => i.confidence !== null);
    if (matched.length === 0) return null;
    const avg = matched.reduce((s, i) => s + (i.confidence ?? 0), 0) / matched.length;
    return Math.round(avg * 100);
  }, [items]);

  const renderDrugCard = useCallback(
    ({ item }: { item: typeof items[0] }) => (
      <DrugCard
        item={item}
        onRemove={handleRemove}
      />
    ),
    [handleRemove],
  );

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <Pressable onPress={() => safeBack('/(tabs)/prescriptions')} style={styles.headerBtn} accessibilityLabel="뒤로" accessibilityRole="button">
          <Text style={styles.headerBtnTxt}>←</Text>
        </Pressable>
        <Text style={styles.headerTitle}>AI 인식 결과</Text>
        <View style={styles.headerBtn} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        {/* OCR 상태 배너 */}
        {ocrStatus && ocrStatus !== 'DONE' && <OcrStatusBanner status={ocrStatus} />}

        {/* 인식 요약 카드 */}
        <View style={styles.summaryCard}>
          <View style={styles.prescriptionThumb}>
            <Text style={styles.prescriptionEmoji}>📋</Text>
          </View>
          <View style={styles.summaryText}>
            <View style={styles.aiBadge}>
              <Text style={styles.aiBadgeTxt}>✨ Gemini Vision · RAG 매칭</Text>
            </View>
            <Text style={styles.summaryCount}>약 {items.length}개 추출됨</Text>
            {averageConfidence !== null && (
              <Text style={styles.summaryConf}>평균 신뢰도 {averageConfidence}%</Text>
            )}
          </View>
        </View>

        {/* 병용금기 경고 — 의료 안전 P0 */}
        {warnings.length > 0 && (
          <View
            style={[
              styles.warningSection,
              criticalCount > 0 && styles.warningSectionCritical,
            ]}
            accessibilityLabel={`병용금기 경고 ${warnings.length}건${criticalCount > 0 ? `, 위험 ${criticalCount}건 포함` : ''}`}
          >
            <Text style={styles.warningTitle}>⚠️ 병용금기 경고 · {warnings.length}건</Text>
            {criticalCount > 0 && (
              <Text style={styles.warningGuide}>
                위험 등급 {criticalCount}건 발견. 약사 또는 의사와 상담해 주세요.
              </Text>
            )}
            <View style={styles.warningList}>
              {warnings.map((w, i) => (
                <DDIWarningCard key={`${w.drugCodeA}-${w.drugCodeB}-${i}`} warning={w} />
              ))}
            </View>
          </View>
        )}

        {/* 약 카드 리스트 */}
        <View style={styles.listHeader}>
          <Text style={styles.listTitle}>인식된 약 · {items.length}</Text>
          <Text style={styles.listHint}>탭해서 편집</Text>
        </View>

        <FlatList
          data={items}
          keyExtractor={(i) => i.id}
          renderItem={renderDrugCard}
          scrollEnabled={false}
          ItemSeparatorComponent={() => <View style={{ height: space.s10 }} />}
          contentContainerStyle={styles.cardList}
        />

        {/* 직접 추가 */}
        <Pressable
          style={styles.addBtn}
          onPress={handleAddManual}
          accessibilityLabel="직접 추가하기"
          accessibilityRole="button"
        >
          <View style={styles.addIcon}><Text style={styles.addIconTxt}>+</Text></View>
          <Text style={styles.addTxt}>직접 추가하기</Text>
        </Pressable>

        {/* 메모 */}
        <View style={styles.memoCard}>
          <Text style={styles.memoLabel}>약봉투 메모</Text>
          <TextInput
            value={memo}
            onChangeText={(t) => dispatch(setMemo(t))}
            placeholder="메모를 입력하세요"
            placeholderTextColor={colors.labelAssistive}
            multiline
            style={styles.memoInput}
            accessibilityLabel="약봉투 메모"
          />
          {/* 빠른 프리필 칩 */}
          <View style={styles.chipRow}>
            {['식후 30분', '공복', '취침 전'].map((chip) => (
              <Pressable
                key={chip}
                style={styles.chip}
                onPress={() => dispatch(setMemo(memo ? `${memo} ${chip}` : chip))}
                accessibilityLabel={chip}
                accessibilityRole="button"
              >
                <Text style={styles.chipTxt}>{chip}</Text>
              </Pressable>
            ))}
          </View>
        </View>
      </ScrollView>

    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal, borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerBtn: { width: scale(40), alignItems: 'center' },
  headerBtnTxt: { fontSize: scale(22), color: colors.labelNormal },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  scroll: { padding: space.s16, paddingBottom: 100 },
  summaryCard: {
    flexDirection: 'row', alignItems: 'center', gap: space.s16,
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    padding: space.s16, borderWidth: 1, borderColor: colors.line, ...shadows.small,
    marginBottom: space.s16,
  },
  prescriptionThumb: { width: scale(64), height: scale(80), borderRadius: radius.r8, backgroundColor: '#F4F1EA', alignItems: 'center', justifyContent: 'center' },
  prescriptionEmoji: { fontSize: scale(28) },
  summaryText: { flex: 1, gap: space.s4 },
  aiBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: space.s8, paddingVertical: space.s4,
    borderRadius: radius.r6, backgroundColor: '#F0EDFF',
  },
  aiBadgeTxt: { ...typography.caption1, color: colors.accentViolet, fontWeight: '600' },
  summaryCount: { ...typography.headline2, color: colors.labelNormal },
  summaryConf: { ...typography.caption1, color: colors.labelAlternative },
  warningSection: {
    backgroundColor: colors.bgNormal,
    borderRadius: radius.r16,
    padding: space.s14,
    marginBottom: space.s16,
    borderWidth: 1, borderColor: colors.line,
    gap: space.s10,
  },
  warningSectionCritical: {
    borderColor: colors.red50,
    borderWidth: 2,
  },
  warningTitle: { fontSize: scale(15), fontWeight: '700', color: colors.labelNormal, letterSpacing: -0.01 },
  warningGuide: { fontSize: scale(13), color: colors.red40, fontWeight: '600', lineHeight: scale(18) },
  warningList: { gap: space.s10 },
  listHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: space.s8 },
  listTitle: { ...typography.label2, color: colors.labelAlternative, fontWeight: '600', textTransform: 'uppercase', letterSpacing: 0.6 },
  listHint: { ...typography.caption1, color: colors.labelAssistive },
  cardList: { gap: space.s10 },
  addBtn: {
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
    borderRadius: radius.r14, borderWidth: 1.5, borderColor: '#A0C4FF', borderStyle: 'dashed',
    backgroundColor: '#F0F7FF', padding: space.s16, marginTop: space.s12,
  },
  addIcon: {
    width: scale(36), height: scale(36), borderRadius: radius.r10,
    backgroundColor: colors.bgNormal, borderWidth: 1, borderColor: '#A0C4FF',
    alignItems: 'center', justifyContent: 'center',
  },
  addIconTxt: { fontSize: scale(22), color: colors.primaryNormal },
  addTxt: { ...typography.label1n, color: colors.labelNormal, fontWeight: '700' },
  memoCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16, padding: space.s16,
    borderWidth: 1, borderColor: colors.line, marginTop: space.s12, gap: space.s8,
  },
  memoLabel: { ...typography.label1n, color: colors.labelNeutral, fontWeight: '700' },
  memoInput: {
    ...typography.body2r, color: colors.labelNormal,
    minHeight: scale(80), textAlignVertical: 'top', paddingTop: 0,
  },
  chipRow: { flexDirection: 'row', gap: space.s6 },
  chip: {
    paddingHorizontal: space.s12, paddingVertical: space.s6,
    borderRadius: radius.full, backgroundColor: colors.bgAlt,
    borderWidth: 1, borderColor: colors.line,
  },
  chipTxt: { ...typography.caption1, color: colors.labelNeutral },
});
