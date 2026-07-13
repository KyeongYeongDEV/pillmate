import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  View, Text, Pressable, ScrollView, StyleSheet, Alert, Modal, BackHandler, TextInput,
} from 'react-native';
import { usePreventRemove } from '@react-navigation/core';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import * as Haptics from 'expo-haptics';
import { scale, colors, typography, space, radius, shadows } from '@/styles/tokens';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  addManual, removeItem,
  addSlot, removeSlot, setSlotTime,
  setStartDate, setEndDate,
  setMemo, setSymptom, reset,
} from '@/store/slices/prescriptionFlowSlice';
import { useRegisterPrescriptionMutation } from '@/store/slices/prescriptionApi';
import { useGetMyGroupsQuery } from '@/store/slices/caregroupApi';
import TimePicker from '@/components/schedule/TimePicker';
import DrugCard from '@/components/prescription/DrugCard';
import DDIWarningCard from '@/components/prescription/DDIWarningCard';
import DurationField from '@/components/prescription/DurationField';
import type { RootState } from '@/store';
import type {
  DrugListItem, PrescriptionSlotDraft, RegisterPrescriptionInput,
  InteractionWarning,
} from '@/types/prescription';
import { MFDS_SOURCE } from '@/lib/constants';
import { deriveTimeOfDay } from '@/lib/prescription/timeOfDay';
import { useSheetBottomPadding } from '@/hooks/useSheetBottomPadding';

function validateRegisterInput(
  items: DrugListItem[],
): { title: string; message: string } | null {
  if (items.length === 0) return { title: '약 목록이 비어있습니다', message: '약을 1개 이상 추가해주세요.' };
  return null;
}

function buildRegisterPayload(
  prescribedAt: string, imageKey: string | null,
  items: DrugListItem[], prescriptionSlots: PrescriptionSlotDraft[],
  startDate: string, endDate: string, careGroupId: number | null,
  label: string | null, memo: string | null, symptom: string | null,
): RegisterPrescriptionInput {
  const today = new Date().toISOString().slice(0, 10);
  return {
    prescribedAt: prescribedAt || today,
    imageKey: imageKey ?? null,
    label: label || null,
    memo: memo || null,
    symptom: symptom || null,
    items: items.map(item => ({
      kdCode: item.kdCode, nameRaw: item.nameRaw, doseAmount: item.doseAmount,
      doseUnit: item.doseUnit, frequency: item.frequency, durationDays: item.durationDays,
      confidence: item.confidence,
    })),
    schedule: {
      careGroupId,
      slots: prescriptionSlots.map(({ timeOfDay, customTime }) => ({ timeOfDay, customTime })),
      startDate: startDate || prescribedAt || today,
      endDate: endDate || null,
    },
  };
}

function addDays(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

function diffDaysInclusive(start: string, end: string): number {
  const [ys, ms, ds] = start.split('-').map(Number);
  const [ye, me, de] = end.split('-').map(Number);
  const diff = Date.UTC(ye, me - 1, de) - Date.UTC(ys, ms - 1, ds);
  return Math.round(diff / 86_400_000) + 1;
}

const DEFAULT_SLOT_TIME = '08:00:00';

export default function PrescriptionReviewScreen() {
  const dispatch = useAppDispatch();
  const { items, prescriptionSlots, prescribedAt, startDate, endDate, imageKey, memo, symptom } =
    useAppSelector((s: RootState) => s.prescriptionFlow);
  const [label, setLabel] = useState('');
  // placeholder 힌트만 — prefill 아님(값은 여전히 '') + 강제 아님. 미입력 시 label=null 전송, BE 가 스마트 기본값 부여.
  const labelPlaceholder = useMemo(() => {
    const now = new Date();
    return `예: ${now.getMonth() + 1}월 ${now.getDate()}일 약봉투`;
  }, []);

  const { data: groups = [] } = useGetMyGroupsQuery();
  const careGroupId = useMemo(() => {
    const pinned = groups.find(g => g.pinned);
    return pinned?.groupId ?? groups[0]?.groupId ?? null;
  }, [groups]);

  const [registerPrescription, { isLoading }] = useRegisterPrescriptionMutation();

  const [manualVisible, setManualVisible] = useState(false);
  const [manualName, setManualName] = useState('');
  const [editingSlotUid, setEditingSlotUid] = useState<string | null>(null);
  const [addTimePickerVisible, setAddTimePickerVisible] = useState(false);
  const [ddiWarnings, setDdiWarnings] = useState<InteractionWarning[] | null>(null);

  // swipe-back / navigation.goBack() 차단 — warnings 확인 전 화면 이탈 방지 (의료 P0)
  usePreventRemove(ddiWarnings !== null, () => {});

  // Android 하드웨어 뒤로가기 차단 — usePreventRemove의 보조 안전망
  useEffect(() => {
    if (!ddiWarnings) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => true);
    return () => sub.remove();
  }, [ddiWarnings]);

  // 화면 이탈 확정(헤더 뒤로가기/하드웨어 백/스와이프) 시 draft 정리 — 허브에 잔존 표시 방지.
  // DDI 경고로 이탈이 막힌 동안은 unmount 자체가 발생하지 않으므로 여기서 건드릴 필요 없음.
  useEffect(() => () => { dispatch(reset()); }, [dispatch]);

  const editingSlot = useMemo(
    () => prescriptionSlots.find(s => s.uid === editingSlotUid) ?? null,
    [prescriptionSlots, editingSlotUid],
  );

  const sortedSlots = useMemo(
    () => [...prescriptionSlots].sort((a, b) => a.customTime.localeCompare(b.customTime)),
    [prescriptionSlots],
  );

  const ddiSheetBottom = useSheetBottomPadding();
  const footerBottom = useSheetBottomPadding(space.s16, space.s8);

  const handleAddManual = useCallback(() => {
    const name = manualName.trim();
    if (!name) return;
    dispatch(addManual({ nameRaw: name }));
    setManualName('');
    setManualVisible(false);
  }, [dispatch, manualName]);

  const handleCancelManual = useCallback(() => {
    setManualName('');
    setManualVisible(false);
  }, []);

  const handleRemoveItem = useCallback((id: string) => {
    dispatch(removeItem(id));
  }, [dispatch]);

  const handleEditSlotTime = useCallback((uid: string) => {
    setEditingSlotUid(uid);
  }, []);

  const handleSlotTimeConfirm = useCallback((time: string) => {
    if (editingSlotUid) dispatch(setSlotTime({ uid: editingSlotUid, customTime: time }));
    setEditingSlotUid(null);
  }, [dispatch, editingSlotUid]);

  const handleRemoveSlot = useCallback((uid: string) => {
    dispatch(removeSlot(uid));
  }, [dispatch]);

  const handleAddSlotConfirm = useCallback((time: string) => {
    dispatch(addSlot({ timeOfDay: deriveTimeOfDay(time), customTime: time }));
    setAddTimePickerVisible(false);
  }, [dispatch]);

  const durationBase = startDate || prescribedAt || new Date().toISOString().slice(0, 10);
  const durationDays = endDate ? diffDaysInclusive(durationBase, endDate) : null;

  const handleDurationChange = useCallback((days: number | null) => {
    if (days === null) {
      dispatch(setEndDate(''));
      return;
    }
    dispatch(setEndDate(addDays(durationBase, days - 1)));
  }, [dispatch, durationBase]);

  const handleRegister = useCallback(async () => {
    const validationErr = validateRegisterInput(items);
    if (validationErr) {
      Alert.alert(validationErr.title, validationErr.message);
      return;
    }
    try {
      const result = await registerPrescription(
        buildRegisterPayload(prescribedAt, imageKey, items, prescriptionSlots, startDate, endDate, careGroupId, label || null, memo || null, symptom || null),
      ).unwrap();
      const warnings = result.warnings ?? [];
      if (warnings.length > 0) {
        // 병용금기 경고 존재 → 4단계 severity 정렬 후 모달 표시. 홈 이동은 사용자 확인 후.
        const SEVERITY_RANK: Record<string, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
        const sorted = [...warnings].sort(
          (a, b) => (SEVERITY_RANK[a.severity] ?? 4) - (SEVERITY_RANK[b.severity] ?? 4),
        );
        setDdiWarnings(sorted);
      } else {
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
        dispatch(reset());
        router.replace('/(tabs)/home');
      }
    } catch {
      Alert.alert('등록 실패', '다시 시도해주세요.');
    }
  }, [items, careGroupId, prescribedAt, imageKey, prescriptionSlots, startDate, endDate, registerPrescription, dispatch]);

  const handleDdiAcknowledge = useCallback(async () => {
    setDdiWarnings(null);
    await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    dispatch(reset());
    router.replace('/(tabs)/home');
  }, [dispatch]);

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <Pressable
          onPress={() => router.back()}
          style={styles.headerBtn}
          accessibilityLabel="뒤로"
          accessibilityRole="button"
          disabled={ddiWarnings !== null}
        >
          <Text style={[styles.headerBtnTxt, ddiWarnings !== null && styles.headerBtnHidden]}>←</Text>
        </Pressable>
        <Text style={styles.headerTitle}>약봉투 등록</Text>
        <View style={styles.headerBtn} />
      </View>

      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {/* ── 처방 이름 / 메모 ──────────────────────── */}
        <SectionHeader title="처방 이름 (선택)" />
        <View style={styles.labelCard}>
          <TextInput
            style={styles.labelInput}
            placeholder={labelPlaceholder}
            placeholderTextColor={colors.labelAssistive}
            value={label}
            onChangeText={setLabel}
            maxLength={100}
            returnKeyType="done"
            accessibilityLabel="처방 이름 입력"
          />
          <View style={styles.inputDivider} />
          <TextInput
            style={styles.memoInput}
            placeholder="간단 메모 (선택)"
            placeholderTextColor={colors.labelAssistive}
            value={memo}
            onChangeText={(t) => dispatch(setMemo(t))}
            maxLength={500}
            multiline
            textAlignVertical="top"
            accessibilityLabel="메모 입력"
          />
          <View style={styles.inputDivider} />
          <TextInput
            style={styles.symptomInput}
            placeholder="진단·증상·병명 (선택)"
            placeholderTextColor={colors.labelAssistive}
            value={symptom}
            onChangeText={(t) => dispatch(setSymptom(t))}
            maxLength={200}
            returnKeyType="done"
            accessibilityLabel="증상·병명 입력"
          />
        </View>

        {/* ── A: 약 목록 ─────────────────────────────── */}
        <SectionHeader title={`약 목록 · ${items.length}종`} />

        {items.length === 0 ? (
          <View style={styles.emptyBox}>
            <Text style={styles.emptyTxt}>추가된 약이 없어요</Text>
          </View>
        ) : (
          <View style={styles.cardList}>
            {items.map(item => (
              <DrugCard
                key={item.id}
                item={item}
                onRemove={handleRemoveItem}
              />
            ))}
          </View>
        )}

        <View style={styles.addRow}>
          <Pressable
            style={styles.addBtn}
            onPress={() => router.push({ pathname: '/prescription/search', params: { mode: 'add' } } as any)}
            accessibilityLabel="약 검색으로 추가"
            accessibilityRole="button"
          >
            <Text style={styles.addBtnTxt}>🔍 검색으로 추가</Text>
          </Pressable>
          <Pressable
            style={styles.addBtn}
            onPress={() => setManualVisible(true)}
            accessibilityLabel="직접 입력으로 추가"
            accessibilityRole="button"
          >
            <Text style={styles.addBtnTxt}>✏️ 직접 입력</Text>
          </Pressable>
        </View>

        {/* ── B: 알림 시간 슬롯 ──────────────────────── */}
        <SectionHeader title="알림 시간" />

        <View style={styles.slotsCard}>
          {sortedSlots.map(slot => (
            <View key={slot.uid} style={styles.slotRow}>
              <Pressable
                style={styles.slotTimePill}
                onPress={() => handleEditSlotTime(slot.uid)}
                accessibilityLabel={`${slot.customTime.slice(0, 5)} 알림 시각 수정`}
                accessibilityRole="button"
              >
                <Text style={styles.slotTimeTxt}>{slot.customTime.slice(0, 5)}</Text>
              </Pressable>
              <Pressable
                onPress={() => handleRemoveSlot(slot.uid)}
                style={styles.slotDeleteBtn}
                accessibilityLabel="슬롯 삭제"
                accessibilityRole="button"
              >
                <Text style={styles.slotDeleteTxt}>×</Text>
              </Pressable>
            </View>
          ))}

          <Pressable
            style={styles.addSlotBtn}
            onPress={() => setAddTimePickerVisible(true)}
            accessibilityLabel="알림 시간 추가"
            accessibilityRole="button"
          >
            <Text style={styles.addSlotTxt}>+ 시간 추가</Text>
          </Pressable>
        </View>

        {/* ── C: 복약 기간 ───────────────────────────── */}
        <SectionHeader title="복약 기간" />

        <View style={styles.durationCard}>
          {prescribedAt ? (
            <Text style={styles.durationDateLabel}>처방일: {prescribedAt}</Text>
          ) : null}
          <View style={styles.dateRangeRow}>
            <Text style={styles.dateRangeLabel}>시작</Text>
            <Text style={styles.dateRangeValue}>{startDate || prescribedAt || '—'}</Text>
            <Text style={styles.dateRangeSep}>→</Text>
            <Text style={styles.dateRangeLabel}>종료</Text>
            <Text style={styles.dateRangeValue}>{endDate || '무기한'}</Text>
          </View>
          <DurationField valueDays={durationDays} onChange={handleDurationChange} />
        </View>

        {/* ── D: 의료 안전 안내 ──────────────────────── */}
        <View style={styles.safetyFooter}>
          <Text style={styles.safetyText}>
            약 정보 출처: {MFDS_SOURCE} · 정확한 복약 정보는 약사·의사와 상담하세요
          </Text>
        </View>
      </ScrollView>

      {/* 등록 버튼 */}
      <View style={[styles.footer, { paddingBottom: footerBottom }]}>
        <Pressable
          style={[styles.registerBtn, isLoading && styles.registerBtnDisabled]}
          onPress={handleRegister}
          disabled={isLoading}
          accessibilityLabel="약봉투 등록"
          accessibilityRole="button"
        >
          <Text style={styles.registerBtnTxt}>{isLoading ? '등록 중…' : '등록하기'}</Text>
        </Pressable>
      </View>

      {/* 직접 입력 — 이름만 간단 입력 (검색 안 되는 약) */}
      <Modal visible={manualVisible} transparent animationType="fade" onRequestClose={handleCancelManual}>
        <Pressable style={styles.manualBackdrop} onPress={handleCancelManual}>
          <Pressable style={styles.manualCard} onPress={() => {}}>
            <Text style={styles.manualTitle}>약 이름 직접 입력</Text>
            <TextInput
              style={styles.manualInput}
              placeholder="예: 타이레놀정 500mg"
              placeholderTextColor={colors.labelAssistive}
              value={manualName}
              onChangeText={setManualName}
              autoFocus
              returnKeyType="done"
              onSubmitEditing={handleAddManual}
            />
            <View style={styles.manualBtnRow}>
              <Pressable style={styles.manualCancelBtn} onPress={handleCancelManual} accessibilityLabel="취소" accessibilityRole="button">
                <Text style={styles.manualCancelTxt}>취소</Text>
              </Pressable>
              <Pressable
                style={[styles.manualConfirmBtn, !manualName.trim() && styles.manualConfirmDisabled]}
                onPress={handleAddManual}
                disabled={!manualName.trim()}
                accessibilityLabel="약 추가"
                accessibilityRole="button"
              >
                <Text style={styles.manualConfirmTxt}>추가</Text>
              </Pressable>
            </View>
          </Pressable>
        </Pressable>
      </Modal>

      {/* 슬롯 시각 편집 TimePicker */}
      <TimePicker
        visible={editingSlotUid !== null}
        initialTime={editingSlot?.customTime ?? '08:00:00'}
        onConfirm={handleSlotTimeConfirm}
        onClose={() => setEditingSlotUid(null)}
      />

      {/* 슬롯 추가 — 시각 직접 선택 TimePicker */}
      <TimePicker
        visible={addTimePickerVisible}
        initialTime={DEFAULT_SLOT_TIME}
        onConfirm={handleAddSlotConfirm}
        onClose={() => setAddTimePickerVisible(false)}
      />

      {/* DDI 병용금기 경고 모달 — 등록 성공 후 warnings 있을 때 */}
      <Modal
        visible={ddiWarnings !== null}
        transparent
        animationType="slide"
        onRequestClose={() => {/* 뒤로가기로 닫기 방지 — 반드시 확인 버튼 사용 */}}
      >
        <View style={styles.ddiBackdrop}>
          <View style={[styles.ddiSheet, { paddingBottom: ddiSheetBottom }]}>
            <View style={styles.ddiTitleRow}>
              <Text style={styles.ddiTitle}>
                ⚠️ 약물 상호작용 주의 ({ddiWarnings?.length ?? 0}건)
              </Text>
            </View>
            <ScrollView
              style={styles.ddiScroll}
              contentContainerStyle={styles.ddiScrollContent}
              showsVerticalScrollIndicator={false}
            >
              {ddiWarnings?.map((w, i) => (
                <DDIWarningCard key={i} warning={w} />
              ))}
              <View style={styles.ddiConsultBox}>
                <Text style={styles.ddiConsultText}>
                  약사·의사와 상담해 주세요
                </Text>
              </View>
            </ScrollView>
            <Pressable
              style={styles.ddiAckBtn}
              onPress={handleDdiAcknowledge}
              accessibilityLabel="병용금기 경고 확인"
              accessibilityRole="button"
            >
              <Text style={styles.ddiAckTxt}>확인했습니다</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

function SectionHeader({ title }: { title: string }) {
  return <Text style={sectionHeaderStyle}>{title}</Text>;
}

const sectionHeaderStyle: import('react-native').TextStyle = {
  fontSize: scale(13),
  fontWeight: '700',
  color: colors.labelAlternative,
  letterSpacing: 0.6,
  textTransform: 'uppercase',
  marginTop: space.s20,
  marginBottom: space.s8,
  paddingHorizontal: 2,
};

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerBtn: { width: scale(40), alignItems: 'center' },
  headerBtnTxt: { fontSize: scale(22), color: colors.labelNormal },
  headerBtnHidden: { opacity: 0 },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  scroll: { padding: space.s16, paddingBottom: 120 },
  emptyBox: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r12,
    padding: space.s24, alignItems: 'center',
    borderWidth: 1, borderColor: colors.line,
  },
  emptyTxt: { ...typography.body2r, color: colors.labelAlternative },
  cardList: { gap: space.s10 },
  addRow: { flexDirection: 'row', gap: space.s10, marginTop: space.s10 },
  addBtn: {
    flex: 1, paddingVertical: space.s14,
    borderRadius: radius.r12, backgroundColor: colors.bgNormal,
    borderWidth: 1.5, borderColor: colors.primaryBase, borderStyle: 'dashed',
    alignItems: 'center',
  },
  addBtnTxt: { ...typography.label2, color: colors.primaryBase, fontWeight: '700' },
  slotsCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, ...shadows.small,
    paddingVertical: space.s8,
  },
  slotRow: {
    flexDirection: 'row', alignItems: 'center', gap: space.s12,
    paddingHorizontal: space.s16, paddingVertical: space.s10,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  slotTimePill: {
    flex: 1, paddingVertical: space.s8, paddingHorizontal: space.s12,
    borderRadius: radius.r8, backgroundColor: colors.bgAlt,
    borderWidth: 1, borderColor: colors.line,
  },
  slotTimeTxt: { ...typography.body1n, color: colors.labelNormal, fontWeight: '700' },
  slotDeleteBtn: {
    width: scale(32), height: scale(32), borderRadius: scale(16),
    backgroundColor: colors.bgAlt, alignItems: 'center', justifyContent: 'center',
  },
  slotDeleteTxt: { fontSize: scale(18), color: colors.labelAlternative },
  addSlotBtn: {
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    alignItems: 'center',
  },
  addSlotTxt: { ...typography.label2, color: colors.primaryNormal, fontWeight: '600' },
  durationCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, ...shadows.small,
    padding: space.s16, gap: space.s12,
  },
  durationDateLabel: { ...typography.caption1, color: colors.labelAssistive },
  dateRangeRow: { flexDirection: 'row', alignItems: 'center', gap: space.s8 },
  dateRangeLabel: { ...typography.caption1, color: colors.labelAlternative },
  dateRangeValue: { ...typography.body1n, color: colors.labelNormal, fontWeight: '700' },
  dateRangeSep: { ...typography.caption1, color: colors.labelAssistive },
  safetyFooter: {
    marginTop: space.s20,
    padding: space.s12, borderRadius: radius.r12,
    backgroundColor: '#F0F7FF', borderWidth: 1, borderColor: '#C8DDFF',
  },
  safetyText: { ...typography.caption1, color: colors.labelAlternative, textAlign: 'center', lineHeight: scale(18) },
  footer: {
    position: 'absolute', bottom: 0, left: 0, right: 0,
    padding: space.s16, backgroundColor: colors.bgNormal,
    borderTopWidth: 1, borderTopColor: colors.line,
  },
  registerBtn: {
    backgroundColor: colors.primaryNormal, borderRadius: radius.r16,
    paddingVertical: space.s16, alignItems: 'center', ...shadows.small,
  },
  registerBtnDisabled: { opacity: 0.6 },
  registerBtnTxt: { ...typography.headline1, color: '#fff' },
  ddiBackdrop: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.55)', justifyContent: 'flex-end',
  },
  ddiSheet: {
    backgroundColor: colors.bgNormal,
    borderTopLeftRadius: 24, borderTopRightRadius: 24,
    maxHeight: '85%',
  },
  ddiTitleRow: {
    paddingHorizontal: space.s20, paddingTop: space.s24, paddingBottom: space.s12,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  ddiTitle: {
    fontSize: scale(17), fontWeight: '700', color: colors.labelNormal, textAlign: 'center',
  },
  ddiScroll: { flexShrink: 1 },
  ddiScrollContent: { padding: space.s16, gap: space.s10 },
  ddiConsultBox: {
    marginTop: space.s4, padding: space.s14,
    borderRadius: radius.r12, backgroundColor: '#F0F7FF',
    borderWidth: 1, borderColor: '#C8DDFF',
  },
  ddiConsultText: {
    fontSize: scale(13), color: colors.labelAlternative,
    textAlign: 'center', lineHeight: scale(18),
  },
  ddiAckBtn: {
    marginHorizontal: space.s16, marginTop: space.s12,
    backgroundColor: colors.primaryNormal, borderRadius: radius.r16,
    paddingVertical: space.s16, alignItems: 'center', ...shadows.small,
  },
  ddiAckTxt: { fontSize: scale(16), fontWeight: '700', color: '#fff' },
  labelCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, ...shadows.small, overflow: 'hidden',
  },
  labelInput: {
    ...typography.body1n, color: colors.labelNormal,
    paddingHorizontal: space.s16, paddingVertical: space.s12, minHeight: scale(44),
  },
  inputDivider: { height: 1, backgroundColor: colors.line, marginHorizontal: space.s16 },
  memoInput: {
    ...typography.body2r, color: colors.labelNormal,
    paddingHorizontal: space.s16, paddingVertical: space.s12, minHeight: scale(64),
  },
  symptomInput: {
    ...typography.body2r, color: colors.labelNormal,
    paddingHorizontal: space.s16, paddingVertical: space.s12, minHeight: scale(44),
  },
  manualBackdrop: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.55)',
    alignItems: 'center', justifyContent: 'center', padding: space.s24,
  },
  manualCard: {
    width: '100%', backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    padding: space.s20, gap: space.s16, ...shadows.medium,
  },
  manualTitle: { ...typography.headline2, color: colors.labelNormal },
  manualInput: {
    ...typography.body1n, color: colors.labelNormal,
    paddingHorizontal: space.s16, paddingVertical: space.s12, minHeight: scale(48),
    borderRadius: radius.r12, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.bgAlt,
  },
  manualBtnRow: { flexDirection: 'row', gap: space.s10 },
  manualCancelBtn: {
    flex: 1, paddingVertical: space.s14, borderRadius: radius.r12,
    borderWidth: 1, borderColor: colors.line, alignItems: 'center',
  },
  manualCancelTxt: { ...typography.label2, color: colors.labelAlternative, fontWeight: '700' },
  manualConfirmBtn: {
    flex: 1, paddingVertical: space.s14, borderRadius: radius.r12,
    backgroundColor: colors.primaryNormal, alignItems: 'center',
  },
  manualConfirmDisabled: { opacity: 0.5 },
  manualConfirmTxt: { ...typography.label2, color: '#fff', fontWeight: '700' },
});
