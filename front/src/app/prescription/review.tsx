import React, { useCallback, useMemo, useState } from 'react';
import {
  View, Text, Pressable, ScrollView, StyleSheet, Alert, Modal,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import * as Haptics from 'expo-haptics';
import { scale, colors, typography, space, radius, shadows } from '@/styles/tokens';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  addFromSearch, removeItem, updateDoseAmount,
  addSlot, removeSlot, setSlotTime,
  setStartDate, setEndDate,
  reset,
} from '@/store/slices/prescriptionFlowSlice';
import { useRegisterPrescriptionMutation } from '@/store/slices/prescriptionApi';
import { useGetMyGroupsQuery } from '@/store/slices/caregroupApi';
import TimePicker from '@/components/schedule/TimePicker';
import DrugCard from '@/components/prescription/DrugCard';
import DrugSearchModal from '@/components/prescription/DrugSearchModal';
import type { RootState } from '@/store';
import type {
  PrescriptionTimeOfDay, DrugSearchResult,
  DrugListItem, PrescriptionSlotDraft, RegisterPrescriptionInput,
} from '@/types/prescription';
import { MFDS_SOURCE } from '@/lib/constants';

function validateRegisterInput(
  items: DrugListItem[], careGroupId: number | null,
): { title: string; message: string } | null {
  if (items.length === 0) return { title: '약 목록이 비어있습니다', message: '약을 1개 이상 추가해주세요.' };
  if (careGroupId == null) return { title: '그룹이 없어요', message: '케어 그룹을 먼저 만들어주세요.' };
  return null;
}

function buildRegisterPayload(
  prescribedAt: string, imageKey: string | null,
  items: DrugListItem[], prescriptionSlots: PrescriptionSlotDraft[],
  startDate: string, endDate: string, careGroupId: number,
): RegisterPrescriptionInput {
  const today = new Date().toISOString().slice(0, 10);
  return {
    prescribedAt: prescribedAt || today,
    imageKey: imageKey ?? null,
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

const TOD_LABEL: Record<PrescriptionTimeOfDay, string> = {
  MORNING: '아침',
  NOON: '점심',
  EVENING: '저녁',
};

const TOD_DEFAULT_TIME: Record<PrescriptionTimeOfDay, string> = {
  MORNING: '08:00:00',
  NOON: '12:30:00',
  EVENING: '19:00:00',
};

const DURATION_PRESETS = ['7일', '14일', '30일', '90일', '무기한'] as const;
const TOD_OPTIONS: PrescriptionTimeOfDay[] = ['MORNING', 'NOON', 'EVENING'];

export default function PrescriptionReviewScreen() {
  const dispatch = useAppDispatch();
  const { items, prescriptionSlots, prescribedAt, startDate, endDate, imageKey } =
    useAppSelector((s: RootState) => s.prescriptionFlow);

  const { data: groups = [] } = useGetMyGroupsQuery();
  const careGroupId = useMemo(() => {
    const pinned = groups.find(g => g.pinned);
    return pinned?.groupId ?? groups[0]?.groupId ?? null;
  }, [groups]);

  const [registerPrescription, { isLoading }] = useRegisterPrescriptionMutation();

  const [searchVisible, setSearchVisible] = useState(false);
  const [editingSlotUid, setEditingSlotUid] = useState<string | null>(null);
  const [addTodPickerVisible, setAddTodPickerVisible] = useState(false);
  const [pendingTod, setPendingTod] = useState<PrescriptionTimeOfDay | null>(null);
  const [addTimePickerVisible, setAddTimePickerVisible] = useState(false);

  const editingSlot = useMemo(
    () => prescriptionSlots.find(s => s.uid === editingSlotUid) ?? null,
    [prescriptionSlots, editingSlotUid],
  );

  const handleDrugSelect = useCallback((drug: DrugSearchResult) => {
    dispatch(addFromSearch({
      kdCode: drug.kdCode,
      nameRaw: drug.name,
      matchedName: drug.name,
      imageUrl: drug.imageUrl,
    }));
  }, [dispatch]);

  const handleDoseChange = useCallback((id: string, amount: number) => {
    dispatch(updateDoseAmount({ id, amount }));
  }, [dispatch]);

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

  const handlePickTod = useCallback((tod: PrescriptionTimeOfDay) => {
    setPendingTod(tod);
    setAddTodPickerVisible(false);
    setAddTimePickerVisible(true);
  }, []);

  const handleAddSlotConfirm = useCallback((time: string) => {
    if (pendingTod) dispatch(addSlot({ timeOfDay: pendingTod, customTime: time }));
    setAddTimePickerVisible(false);
    setPendingTod(null);
  }, [dispatch, pendingTod]);

  const handleDurationPreset = useCallback((preset: string) => {
    const base = startDate || prescribedAt || new Date().toISOString().slice(0, 10);
    if (preset === '무기한') {
      dispatch(setEndDate(''));
      return;
    }
    const days = parseInt(preset, 10);
    dispatch(setEndDate(addDays(base, days - 1)));
  }, [dispatch, startDate, prescribedAt]);

  const handleRegister = useCallback(async () => {
    const validationErr = validateRegisterInput(items, careGroupId);
    if (validationErr) {
      Alert.alert(validationErr.title, validationErr.message);
      return;
    }
    try {
      await registerPrescription(
        buildRegisterPayload(prescribedAt, imageKey, items, prescriptionSlots, startDate, endDate, careGroupId!),
      ).unwrap();
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      dispatch(reset());
      router.replace('/(tabs)/home');
    } catch {
      Alert.alert('등록 실패', '다시 시도해주세요.');
    }
  }, [items, careGroupId, prescribedAt, imageKey, prescriptionSlots, startDate, endDate, registerPrescription, dispatch]);

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <Pressable
          onPress={() => router.back()}
          style={styles.headerBtn}
          accessibilityLabel="뒤로"
          accessibilityRole="button"
        >
          <Text style={styles.headerBtnTxt}>←</Text>
        </Pressable>
        <Text style={styles.headerTitle}>약봉투 검토 · 등록</Text>
        <View style={styles.headerBtn} />
      </View>

      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
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
                onDoseChange={handleDoseChange}
                onRemove={handleRemoveItem}
              />
            ))}
          </View>
        )}

        <View style={styles.addRow}>
          <Pressable
            style={styles.addBtn}
            onPress={() => setSearchVisible(true)}
            accessibilityLabel="약 검색으로 추가"
            accessibilityRole="button"
          >
            <Text style={styles.addBtnTxt}>🔍 검색으로 추가</Text>
          </Pressable>
          <Pressable
            style={styles.addBtn}
            onPress={() => router.push('/prescription/manual' as any)}
            accessibilityLabel="직접 입력으로 추가"
            accessibilityRole="button"
          >
            <Text style={styles.addBtnTxt}>✏️ 직접 입력</Text>
          </Pressable>
        </View>

        {/* ── B: 알림 시간 슬롯 ──────────────────────── */}
        <SectionHeader title="알림 시간" />

        <View style={styles.slotsCard}>
          {prescriptionSlots.map(slot => (
            <View key={slot.uid} style={styles.slotRow}>
              <View style={styles.slotLabelBadge}>
                <Text style={styles.slotLabelTxt}>{TOD_LABEL[slot.timeOfDay]}</Text>
              </View>
              <Pressable
                style={styles.slotTimePill}
                onPress={() => handleEditSlotTime(slot.uid)}
                accessibilityLabel={`${TOD_LABEL[slot.timeOfDay]} 알림 시각 수정`}
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
            onPress={() => setAddTodPickerVisible(true)}
            accessibilityLabel="알림 슬롯 추가"
            accessibilityRole="button"
          >
            <Text style={styles.addSlotTxt}>+ 슬롯 추가</Text>
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
          <View style={styles.presetRow}>
            {DURATION_PRESETS.map(p => (
              <Pressable
                key={p}
                style={styles.presetChip}
                onPress={() => handleDurationPreset(p)}
                accessibilityLabel={p}
                accessibilityRole="button"
              >
                <Text style={styles.presetChipTxt}>{p}</Text>
              </Pressable>
            ))}
          </View>
        </View>

        {/* ── D: 의료 안전 안내 ──────────────────────── */}
        <View style={styles.safetyFooter}>
          <Text style={styles.safetyText}>
            약 정보 출처: {MFDS_SOURCE} · 정확한 복약 정보는 약사·의사와 상담하세요
          </Text>
        </View>
      </ScrollView>

      {/* 등록 버튼 */}
      <View style={styles.footer}>
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

      {/* 약 검색 모달 */}
      <DrugSearchModal
        visible={searchVisible}
        title="약 검색으로 추가"
        onClose={() => setSearchVisible(false)}
        onSelect={handleDrugSelect}
      />

      {/* 슬롯 시각 편집 TimePicker */}
      <TimePicker
        visible={editingSlotUid !== null}
        initialTime={editingSlot?.customTime ?? '08:00:00'}
        onConfirm={handleSlotTimeConfirm}
        onClose={() => setEditingSlotUid(null)}
      />

      {/* 슬롯 추가 — 시간대 선택 모달 */}
      <Modal
        visible={addTodPickerVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setAddTodPickerVisible(false)}
      >
        <Pressable
          style={styles.modalBackdrop}
          onPress={() => setAddTodPickerVisible(false)}
          accessibilityLabel="닫기"
        />
        <View style={styles.todSheet}>
          <Text style={styles.todSheetTitle}>시간대 선택</Text>
          {TOD_OPTIONS.map(tod => (
            <Pressable
              key={tod}
              style={styles.todOption}
              onPress={() => handlePickTod(tod)}
              accessibilityLabel={TOD_LABEL[tod]}
              accessibilityRole="button"
            >
              <Text style={styles.todOptionTxt}>
                {TOD_LABEL[tod]} <Text style={styles.todOptionTime}>({TOD_DEFAULT_TIME[tod].slice(0, 5)} 기본)</Text>
              </Text>
            </Pressable>
          ))}
          <Pressable
            style={styles.todCancelBtn}
            onPress={() => setAddTodPickerVisible(false)}
            accessibilityRole="button"
          >
            <Text style={styles.todCancelTxt}>취소</Text>
          </Pressable>
        </View>
      </Modal>

      {/* 슬롯 추가 — 시각 선택 TimePicker */}
      <TimePicker
        visible={addTimePickerVisible}
        initialTime={pendingTod ? TOD_DEFAULT_TIME[pendingTod] : '08:00:00'}
        onConfirm={handleAddSlotConfirm}
        onClose={() => { setAddTimePickerVisible(false); setPendingTod(null); }}
      />
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
  slotLabelBadge: {
    width: scale(44), paddingVertical: space.s4,
    borderRadius: radius.r8, backgroundColor: colors.fillNormal,
    alignItems: 'center',
  },
  slotLabelTxt: { ...typography.label2, color: colors.labelNeutral, fontWeight: '700' },
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
  presetRow: { flexDirection: 'row', flexWrap: 'wrap', gap: space.s8 },
  presetChip: {
    paddingHorizontal: space.s12, paddingVertical: space.s8,
    borderRadius: radius.r8, backgroundColor: colors.bgAlt,
    borderWidth: 1, borderColor: colors.line,
  },
  presetChipTxt: { ...typography.label2, color: colors.labelNeutral },
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
  modalBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.45)',
  },
  todSheet: {
    position: 'absolute', bottom: 0, left: 0, right: 0,
    backgroundColor: colors.bgNormal,
    borderTopLeftRadius: 20, borderTopRightRadius: 20,
    paddingTop: space.s24, paddingBottom: space.s40,
    paddingHorizontal: space.s24, gap: space.s4,
  },
  todSheetTitle: {
    fontSize: scale(17), fontWeight: '700', color: colors.labelNormal,
    textAlign: 'center', marginBottom: space.s16,
  },
  todOption: {
    paddingVertical: space.s16,
    borderBottomWidth: 1, borderBottomColor: colors.line,
    alignItems: 'center',
  },
  todOptionTxt: { ...typography.body1n, color: colors.labelNormal, fontWeight: '600' },
  todOptionTime: { ...typography.caption1, color: colors.labelAssistive, fontWeight: '400' },
  todCancelBtn: {
    paddingVertical: space.s16, alignItems: 'center', marginTop: space.s8,
  },
  todCancelTxt: { ...typography.body1n, color: colors.labelAlternative },
});
