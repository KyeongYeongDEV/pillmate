import React, { useState, useCallback } from 'react';
import {
  View, Text, Pressable, ScrollView, TextInput,
  StyleSheet, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import { useAppDispatch } from '@/store/hooks';
import { addManual } from '@/store/slices/prescriptionFlowSlice';
import DrugSearchAutocomplete from '@/components/prescription/DrugSearchAutocomplete';
import DoseStepper from '@/components/prescription/DoseStepper';
import type { DrugSearchResult } from '@/types/prescription';
import { safeBack } from '@/lib/router/safeBack';

const SHAPES = ['원형', '타원형', '캡슐', '기타'] as const;
const COLORS = [
  { id: 'white',     hex: '#FFFFFF', label: '흰색' },
  { id: 'yellow',    hex: '#FFE074', label: '노랑' },
  { id: 'blue',      hex: '#9EC5FF', label: '파랑' },
  { id: 'lightBlue', hex: '#A1E1FF', label: '하늘' },
  { id: 'pink',      hex: '#FFB8F3', label: '분홍' },
  { id: 'orange',    hex: '#FFC06E', label: '주황' },
  { id: 'violet',    hex: '#C0B0FF', label: '보라' },
  { id: 'lime',      hex: '#ACFCC7', label: '연두' },
  { id: 'black',     hex: '#1A1A1A', label: '기타' },
] as const;
const DURATION_PRESETS = ['7일', '14일', '30일', '90일', '장기'] as const;
const DOSE_UNITS = ['정', '캡슐', 'mL', 'mg'] as const;

export default function ManualScreen() {
  const dispatch = useAppDispatch();
  const [nameRaw, setNameRaw] = useState('');
  const [selectedShape, setSelectedShape] = useState<string>('원형');
  const [selectedColor, setSelectedColor] = useState<string>('white');
  const [doseAmount, setDoseAmount] = useState(1);
  const [doseUnit, setDoseUnit] = useState<string>('정');
  const [durationPreset, setDurationPreset] = useState<string>('7일');
  const [memo, setMemo] = useState('');
  const [searchMode, setSearchMode] = useState(false);
  const [matched, setMatched] = useState<DrugSearchResult | null>(null);

  const handleSelect = useCallback((drug: DrugSearchResult) => {
    setNameRaw(drug.name);
    setMatched(drug);
    setSearchMode(false);
  }, []);

  const handleAdd = useCallback(async () => {
    if (!nameRaw.trim()) return;
    const durationDays = durationPreset === '장기' ? 365
      : parseInt(durationPreset, 10);
    dispatch(addManual({
      nameRaw: nameRaw.trim(),
      doseAmount,
      doseUnit,
      frequency: 1,
      durationDays,
    }));
    await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    safeBack('/prescription/review');
  }, [nameRaw, doseAmount, doseUnit, durationPreset, dispatch]);

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <Pressable onPress={() => safeBack('/prescription')} style={styles.headerBtn} accessibilityLabel="닫기" accessibilityRole="button">
          <Text style={styles.headerBtnTxt}>✕</Text>
        </Pressable>
        <View>
          <Text style={styles.headerTitle}>약 직접 추가</Text>
          <Text style={styles.headerSub}>약봉투에 없는 약도 등록할 수 있어요</Text>
        </View>
        <Pressable
          onPress={() => { setNameRaw(''); setMatched(null); setDoseAmount(1); }}
          style={styles.headerBtn}
          accessibilityLabel="초기화"
          accessibilityRole="button"
        >
          <Text style={styles.resetTxt}>초기화</Text>
        </Pressable>
      </View>

      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
          {/* 의료 안전 배너 */}
          <View style={styles.safetyBanner}>
            <Text style={styles.safetyTxt}>
              ✨ 직접 입력한 약은 식약처 DB 자동 검증을 받지 않아요.{'\n'}
              <Text style={styles.safetyBold}>약사·의사 상담을 권장합니다.</Text>
            </Text>
          </View>

          {/* Section: 약 이름 */}
          <Section title="약 이름" required>
            {searchMode ? (
              <DrugSearchAutocomplete onSelect={handleSelect} />
            ) : (
              <View style={styles.nameRow}>
                <TextInput
                  value={nameRaw}
                  onChangeText={(t) => { setNameRaw(t); setMatched(null); }}
                  placeholder="약 이름 입력"
                  placeholderTextColor={colors.labelAssistive}
                  style={styles.nameInput}
                  accessibilityLabel="약 이름"
                  autoCapitalize="none"
                />
                <Pressable onPress={() => setSearchMode(true)} style={styles.searchBtn} accessibilityLabel="DB 검색" accessibilityRole="button">
                  <Text style={styles.searchBtnTxt}>🔍 DB 검색</Text>
                </Pressable>
              </View>
            )}
            {matched && (
              <Text style={styles.matchedHint}>✅ 식약처 DB 매칭됨: {matched.name}</Text>
            )}
            <Text style={styles.nameHint}>식약처 DB에서 찾으면 자동으로 정보가 채워져요</Text>
          </Section>

          {/* Section: 모양 */}
          <Section title="모양">
            <View style={styles.shapeRow}>
              {SHAPES.map((s) => (
                <Pressable
                  key={s}
                  style={[styles.shapeChip, selectedShape === s && styles.shapeChipOn]}
                  onPress={() => setSelectedShape(s)}
                  accessibilityLabel={s}
                  accessibilityRole="radio"
                  accessibilityState={{ checked: selectedShape === s }}
                >
                  <Text style={[styles.shapeLabel, selectedShape === s && styles.shapeLabelOn]}>{s}</Text>
                </Pressable>
              ))}
            </View>
          </Section>

          {/* Section: 색깔 */}
          <Section title="색깔">
            <View style={styles.colorRow}>
              {COLORS.map(({ id, hex, label }) => (
                <Pressable
                  key={id}
                  style={styles.colorItem}
                  onPress={() => setSelectedColor(id)}
                  accessibilityLabel={label}
                  accessibilityRole="radio"
                  accessibilityState={{ checked: selectedColor === id }}
                >
                  <View style={[styles.colorCircle, { backgroundColor: hex }, selectedColor === id && styles.colorCircleOn]} />
                  <Text style={[styles.colorLabel, selectedColor === id && styles.colorLabelOn]}>{label}</Text>
                </Pressable>
              ))}
            </View>
          </Section>

          {/* Section: 1회 복용량 */}
          <Section title="1회 복용량" required>
            <View style={styles.doseRow}>
              <DoseStepper value={doseAmount} unit={doseUnit} onChange={setDoseAmount} />
              <View style={styles.unitRow}>
                {DOSE_UNITS.map((u) => (
                  <Pressable
                    key={u}
                    style={[styles.unitChip, doseUnit === u && styles.unitChipOn]}
                    onPress={() => setDoseUnit(u)}
                    accessibilityLabel={u}
                    accessibilityRole="radio"
                  >
                    <Text style={[styles.unitTxt, doseUnit === u && styles.unitTxtOn]}>{u}</Text>
                  </Pressable>
                ))}
              </View>
            </View>
          </Section>

          {/* Section: 복용 기간 */}
          <Section title="복용 기간">
            <View style={styles.durationRow}>
              {DURATION_PRESETS.map((p) => (
                <Pressable
                  key={p}
                  style={[styles.durationChip, durationPreset === p && styles.durationChipOn]}
                  onPress={() => setDurationPreset(p)}
                  accessibilityLabel={p}
                  accessibilityRole="radio"
                >
                  <Text style={[styles.durationTxt, durationPreset === p && styles.durationTxtOn]}>{p}</Text>
                </Pressable>
              ))}
            </View>
          </Section>

          {/* Section: 메모 */}
          <Section title="메모">
            <TextInput
              value={memo}
              onChangeText={setMemo}
              placeholder="메모 (선택)"
              placeholderTextColor={colors.labelAssistive}
              multiline
              style={styles.memoInput}
              accessibilityLabel="메모"
            />
          </Section>

          {/* 출처 표시 */}
          <Text style={styles.sourceNote}>출처: 사용자 입력 (식약처 미검증)</Text>
        </ScrollView>
      </KeyboardAvoidingView>

      {/* 하단 추가 버튼 */}
      <View style={styles.footer}>
        <Pressable
          style={[styles.addBtn, !nameRaw.trim() && styles.addBtnDisabled]}
          onPress={handleAdd}
          disabled={!nameRaw.trim()}
          accessibilityLabel="추가하기"
          accessibilityRole="button"
        >
          <Text style={styles.addBtnTxt}>추가하기</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

function Section({ title, required, children }: { title: string; required?: boolean; children: React.ReactNode }) {
  return (
    <View style={sectionStyles.section}>
      <Text style={sectionStyles.title}>
        {title}{required && <Text style={sectionStyles.required}> *</Text>}
      </Text>
      {children}
    </View>
  );
}

const sectionStyles = StyleSheet.create({
  section: { gap: space.s8 },
  title: { ...typography.label1n, color: colors.labelNeutral, fontWeight: '700' },
  required: { color: colors.statusNegative },
});

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal, borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerBtn: { width: scale(44), alignItems: 'center' },
  headerBtnTxt: { fontSize: scale(20), color: colors.labelNormal },
  headerTitle: { ...typography.headline1, color: colors.labelNormal, textAlign: 'center' },
  headerSub: { ...typography.caption1, color: colors.labelAlternative, textAlign: 'center' },
  resetTxt: { ...typography.label2, color: colors.labelAlternative },
  scroll: { padding: space.s16, gap: space.s20, paddingBottom: 100 },
  safetyBanner: {
    backgroundColor: '#FFF3E0', borderRadius: radius.r12,
    padding: space.s12, borderWidth: 1, borderColor: colors.statusCautionary,
  },
  safetyTxt: { ...typography.caption1, color: colors.labelNeutral },
  safetyBold: { fontWeight: '700', color: colors.labelNormal },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: space.s8 },
  nameInput: {
    flex: 1, height: scale(52), backgroundColor: colors.bgNormal, borderRadius: radius.r12,
    borderWidth: 1.5, borderColor: colors.primaryNormal,
    paddingHorizontal: space.s12, ...typography.body1n, color: colors.labelNormal,
  },
  searchBtn: {
    paddingHorizontal: space.s12, paddingVertical: space.s8,
    borderRadius: radius.r8, backgroundColor: '#EBF3FF',
  },
  searchBtnTxt: { ...typography.label2, color: colors.primaryNormal, fontWeight: '700' },
  matchedHint: { ...typography.caption1, color: colors.statusPositive, fontWeight: '600' },
  nameHint: { ...typography.caption1, color: colors.labelAssistive },
  shapeRow: { flexDirection: 'row', gap: space.s8 },
  shapeChip: {
    flex: 1, paddingVertical: space.s12, borderRadius: radius.r12,
    backgroundColor: colors.bgNormal, borderWidth: 1, borderColor: colors.line,
    alignItems: 'center',
  },
  shapeChipOn: { borderColor: colors.primaryNormal, borderWidth: 1.5 },
  shapeLabel: { ...typography.label2, color: colors.labelAlternative },
  shapeLabelOn: { color: colors.labelNormal, fontWeight: '700' },
  colorRow: { flexDirection: 'row', flexWrap: 'wrap', gap: space.s12 },
  colorItem: { alignItems: 'center', gap: space.s4, width: scale(44) },
  colorCircle: { width: scale(36), height: scale(36), borderRadius: scale(18), borderWidth: 1, borderColor: colors.line },
  colorCircleOn: { borderWidth: 2.5, borderColor: colors.primaryNormal },
  colorLabel: { ...typography.caption1, color: colors.labelAssistive },
  colorLabelOn: { color: colors.labelNormal, fontWeight: '700' },
  doseRow: { gap: space.s8 },
  unitRow: { flexDirection: 'row', gap: space.s6 },
  unitChip: {
    paddingHorizontal: space.s12, paddingVertical: space.s8,
    borderRadius: radius.r8, backgroundColor: colors.bgAlt, borderWidth: 1, borderColor: colors.line,
  },
  unitChipOn: { backgroundColor: colors.primaryNormal, borderColor: colors.primaryNormal },
  unitTxt: { ...typography.label2, color: colors.labelAlternative },
  unitTxtOn: { color: '#fff', fontWeight: '700' },
  durationRow: { flexDirection: 'row', flexWrap: 'wrap', gap: space.s8 },
  durationChip: {
    paddingHorizontal: space.s12, paddingVertical: space.s8,
    borderRadius: radius.r8, backgroundColor: colors.bgNormal, borderWidth: 1, borderColor: colors.line,
  },
  durationChipOn: { backgroundColor: colors.labelNormal, borderColor: colors.labelNormal },
  durationTxt: { ...typography.label2, color: colors.labelNeutral },
  durationTxtOn: { color: '#fff', fontWeight: '700' },
  memoInput: {
    ...typography.body2r, color: colors.labelNormal,
    backgroundColor: colors.bgNormal, borderRadius: radius.r12,
    borderWidth: 1, borderColor: colors.line,
    padding: space.s12, minHeight: scale(80), textAlignVertical: 'top',
  },
  sourceNote: { ...typography.caption1, color: colors.labelAssistive, textAlign: 'center' },
  footer: {
    position: 'absolute', bottom: 0, left: 0, right: 0,
    padding: space.s16, backgroundColor: colors.bgNormal,
    borderTopWidth: 1, borderTopColor: colors.line,
  },
  addBtn: {
    backgroundColor: colors.primaryNormal, borderRadius: radius.r16,
    paddingVertical: space.s16, alignItems: 'center',
  },
  addBtnDisabled: { opacity: 0.5 },
  addBtnTxt: { ...typography.headline1, color: '#fff' },
});
