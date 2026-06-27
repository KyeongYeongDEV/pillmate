import React, { useCallback, useRef, useState } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable, ActivityIndicator,
  Alert, Animated, Modal, TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import {
  useGetPrescriptionDetailQuery,
  useUpdatePrescriptionMutation,
  useDeletePrescriptionMutation,
} from '@/store/slices/prescriptionApi';
import {
  useGetPrescriptionSlotsQuery,
  useUpdateScheduleTimeMutation,
  useAddPrescriptionSlotMutation,
  useRemovePrescriptionSlotMutation,
  useUpdatePrescriptionPeriodMutation,
} from '@/store/slices/scheduleApi';
import PrescriptionDrugRow from '@/components/prescription/PrescriptionDrugRow';
import InsightCard from '@/components/home/InsightCard';
import TimePicker, { formatTimeHHmm } from '@/components/schedule/TimePicker';
import { Image as ExpoImage } from 'expo-image';
import { safeBack } from '@/lib/router/safeBack';
import { scale, colors, space, radius, typography, shadows } from '@/styles/tokens';
import type { PrescriptionDetailView, NutrientNote } from '@/types/prescription';
import type { SlotEditView, TimeOfDay } from '@/types/schedule';

const PERIOD_ENDED_CODES = ['PILL_032', 'PILL_035'];
const TOAST_DURATION_MS = 2500;

const PERIOD_QUICK_OPTIONS = [
  { label: '+7일',  days: 7  },
  { label: '+30일', days: 30 },
  { label: '+90일', days: 90 },
];

function addDaysToEndDate(currentEnd: string | null | undefined, days: number): string {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const end = currentEnd ? new Date(currentEnd + 'T00:00:00') : null;
  const base = end && end >= today ? new Date(end) : new Date(today);
  base.setDate(base.getDate() + days);
  return base.toISOString().slice(0, 10);
}

const TIME_OF_DAY_LABEL: Record<string, string> = {
  MORNING: '아침',
  NOON:    '점심',
  EVENING: '저녁',
};

const TOD_OPTIONS: TimeOfDay[] = ['MORNING', 'NOON', 'EVENING'];

const TOD_DEFAULT_TIME: Record<TimeOfDay, string> = {
  MORNING: '08:00:00',
  NOON:    '12:30:00',
  EVENING: '19:00:00',
};

export default function PrescriptionDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const prescriptionId = Number(id);

  const { data, isLoading, isError, refetch } = useGetPrescriptionDetailQuery(prescriptionId);
  const {
    data: slots = [],
    isLoading: slotsLoading,
    refetch: refetchSlots,
  } = useGetPrescriptionSlotsQuery(prescriptionId);
  const [updateTime]       = useUpdateScheduleTimeMutation();
  const [addSlot]          = useAddPrescriptionSlotMutation();
  const [removeSlot]       = useRemovePrescriptionSlotMutation();
  const [updatePresc]      = useUpdatePrescriptionMutation();
  const [deletePresc, { isLoading: deleteLoading }] = useDeletePrescriptionMutation();
  const [updatePeriod]     = useUpdatePrescriptionPeriodMutation();

  const [pickerSlot,       setPickerSlot]       = useState<SlotEditView | null>(null);
  const [addTodVisible,    setAddTodVisible]    = useState(false);
  const [periodSheetVisible, setPeriodSheetVisible] = useState(false);
  const [addPickerTod,  setAddPickerTod]  = useState<TimeOfDay | null>(null);
  const [memoEditing,   setMemoEditing]   = useState(false);
  const [memoText,      setMemoText]      = useState('');
  const [memoSaving,    setMemoSaving]    = useState(false);
  const [labelEditing,  setLabelEditing]  = useState(false);
  const [labelText,     setLabelText]     = useState('');
  const [labelSaving,   setLabelSaving]   = useState(false);
  const [toastMsg,      setToastMsg]      = useState('');
  const [toastVisible,  setToastVisible]  = useState(false);
  const toastOpacity = useRef(new Animated.Value(0)).current;
  const toastTimer   = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = useCallback((message: string) => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
    setToastMsg(message);
    setToastVisible(true);
    Animated.sequence([
      Animated.timing(toastOpacity, { toValue: 1, duration: 180, useNativeDriver: true }),
      Animated.delay(TOAST_DURATION_MS - 360),
      Animated.timing(toastOpacity, { toValue: 0, duration: 180, useNativeDriver: true }),
    ]).start(() => {
      toastTimer.current = setTimeout(() => setToastVisible(false), 50);
    });
  }, [toastOpacity]);

  const handleTimeConfirm = useCallback(async (customTime: string) => {
    if (!pickerSlot) return;
    setPickerSlot(null);
    try {
      await updateTime({ scheduleId: pickerSlot.scheduleId, customTime }).unwrap();
      refetchSlots();
    } catch (err: any) {
      const code = err?.data?.error?.code ?? err?.code ?? '';
      if (PERIOD_ENDED_CODES.includes(code)) {
        showToast('기간이 지난 처방전이라 수정할 수 없어요');
      } else {
        Alert.alert('오류', '시간 변경 중 문제가 발생했습니다.');
      }
    }
  }, [pickerSlot, updateTime, refetchSlots, showToast]);

  const handleAddSlotTod = useCallback((tod: TimeOfDay) => {
    setAddTodVisible(false);
    setAddPickerTod(tod);
  }, []);

  const handleAddSlotConfirm = useCallback(async (customTime: string) => {
    const tod = addPickerTod;
    setAddPickerTod(null);
    if (!tod) return;
    try {
      await addSlot({ prescriptionId, timeOfDay: tod, customTime }).unwrap();
    } catch (err: any) {
      const code = err?.data?.error?.code ?? err?.code ?? '';
      if (PERIOD_ENDED_CODES.includes(code)) {
        showToast('기간이 지난 처방전이라 수정할 수 없어요');
      } else {
        Alert.alert('오류', '알림 추가 중 문제가 발생했습니다.');
      }
    }
  }, [addPickerTod, addSlot, prescriptionId, showToast]);

  const handleRemoveSlot = useCallback(async (slot: SlotEditView) => {
    try {
      await removeSlot({ prescriptionId, timeOfDay: slot.timeOfDay }).unwrap();
    } catch (err: any) {
      const code = err?.data?.error?.code ?? err?.code ?? '';
      if (PERIOD_ENDED_CODES.includes(code)) {
        showToast('기간이 지난 처방전이라 수정할 수 없어요');
      } else {
        Alert.alert('오류', '알림 삭제 중 문제가 발생했습니다.');
      }
    }
  }, [removeSlot, prescriptionId, showToast]);

  const startMemoEdit = useCallback(() => {
    setMemoText(data?.memo ?? '');
    setMemoEditing(true);
  }, [data?.memo]);

  const handleMemoSave = useCallback(async () => {
    setMemoSaving(true);
    try {
      await updatePresc({ id: prescriptionId, memo: memoText || null }).unwrap();
      setMemoEditing(false);
    } catch {
      Alert.alert('오류', '메모 저장에 실패했습니다.');
    } finally {
      setMemoSaving(false);
    }
  }, [memoText, updatePresc, prescriptionId]);

  const handleMemoCancel = useCallback(() => setMemoEditing(false), []);

  const startLabelEdit = useCallback(() => {
    setLabelText(data?.label ?? '');
    setLabelEditing(true);
  }, [data?.label]);

  const handleLabelSave = useCallback(async () => {
    setLabelSaving(true);
    try {
      await updatePresc({ id: prescriptionId, label: labelText || null }).unwrap();
      setLabelEditing(false);
    } catch {
      Alert.alert('오류', '이름 저장에 실패했습니다.');
    } finally {
      setLabelSaving(false);
    }
  }, [labelText, updatePresc, prescriptionId]);

  const handleLabelCancel = useCallback(() => setLabelEditing(false), []);

  const handlePeriodUpdate = useCallback(async (days: number) => {
    setPeriodSheetVisible(false);
    const endDate = addDaysToEndDate(data?.periodEnd, days);
    try {
      await updatePeriod({ prescriptionId, endDate }).unwrap();
      refetch();
      refetchSlots();
      showToast('복약 기간이 수정됐어요');
    } catch {
      Alert.alert('오류', '기간 수정에 실패했습니다. 다시 시도해 주세요.');
    }
  }, [data?.periodEnd, updatePeriod, prescriptionId, refetch, refetchSlots, showToast]);

  const handleDelete = useCallback(() => {
    Alert.alert(
      '약봉투 삭제',
      '이 약봉투를 삭제할까요?\n연결된 복약 알림도 함께 삭제됩니다.',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '삭제',
          style: 'destructive',
          onPress: async () => {
            try {
              await deletePresc(prescriptionId).unwrap();
              safeBack('/(tabs)/prescriptions');
            } catch {
              Alert.alert('오류', '삭제에 실패했습니다. 다시 시도해 주세요.');
            }
          },
        },
      ],
      { cancelable: true },
    );
  }, [deletePresc, prescriptionId]);

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <Header />
      {renderBody()}

      <TimePicker
        visible={!!pickerSlot}
        initialTime={pickerSlot?.time ?? '08:00'}
        onConfirm={handleTimeConfirm}
        onClose={() => setPickerSlot(null)}
      />

      <Modal
        visible={addTodVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setAddTodVisible(false)}
      >
        <Pressable style={styles.modalBackdrop} onPress={() => setAddTodVisible(false)} accessibilityLabel="닫기" />
        <View style={styles.todSheet}>
          <Text style={styles.todSheetTitle}>시간대 선택</Text>
          {TOD_OPTIONS.map(tod => (
            <Pressable
              key={tod}
              style={styles.todOption}
              onPress={() => handleAddSlotTod(tod)}
              accessibilityLabel={TIME_OF_DAY_LABEL[tod]}
              accessibilityRole="button"
            >
              <Text style={styles.todOptionTxt}>{TIME_OF_DAY_LABEL[tod]}</Text>
            </Pressable>
          ))}
          <Pressable style={styles.todCancelBtn} onPress={() => setAddTodVisible(false)} accessibilityRole="button">
            <Text style={styles.todCancelTxt}>취소</Text>
          </Pressable>
        </View>
      </Modal>

      <TimePicker
        visible={addPickerTod !== null}
        initialTime={addPickerTod ? TOD_DEFAULT_TIME[addPickerTod] : '08:00:00'}
        onConfirm={handleAddSlotConfirm}
        onClose={() => setAddPickerTod(null)}
      />

      <Modal
        visible={periodSheetVisible}
        transparent
        animationType="slide"
        onRequestClose={() => setPeriodSheetVisible(false)}
      >
        <Pressable style={styles.modalBackdrop} onPress={() => setPeriodSheetVisible(false)} accessibilityLabel="닫기" />
        <View style={styles.periodSheet}>
          <Text style={styles.periodSheetTitle}>복약 기간 수정</Text>
          <Text style={styles.periodSheetHint}>
            영양제처럼 계속 드시는 약은{'\n'}종료일을 길게 잡으세요
          </Text>
          {PERIOD_QUICK_OPTIONS.map(({ label, days }) => (
            <Pressable
              key={days}
              style={styles.periodOption}
              onPress={() => handlePeriodUpdate(days)}
              accessibilityLabel={`${label} 연장`}
              accessibilityRole="button"
            >
              <Text style={styles.periodOptionTxt}>{label} 연장</Text>
            </Pressable>
          ))}
          <Pressable style={styles.todCancelBtn} onPress={() => setPeriodSheetVisible(false)} accessibilityRole="button">
            <Text style={styles.todCancelTxt}>취소</Text>
          </Pressable>
        </View>
      </Modal>

      {toastVisible && (
        <Animated.View style={[styles.toast, { opacity: toastOpacity }]} pointerEvents="none">
          <Text style={styles.toastTxt}>{toastMsg}</Text>
        </Animated.View>
      )}
    </SafeAreaView>
  );

  function renderBody() {
    if (isLoading) return <ActivityIndicator size="large" color={colors.primaryBase} style={styles.loader} />;
    if (isError || !data) return <ErrorState onRetry={refetch} />;
    return (
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <LabelHeader
          label={data.label}
          prescribedAt={data.prescribedAt}
          editing={labelEditing}
          text={labelText}
          saving={labelSaving}
          onChangeText={setLabelText}
          onEdit={startLabelEdit}
          onSave={handleLabelSave}
          onCancel={handleLabelCancel}
        />

        <DetailInfoCard data={data} onEditPeriod={() => setPeriodSheetVisible(true)} />

        {data.symptom ? (
          <View style={styles.symptomRow}>
            <Text style={styles.symptomLabel}>진단·증상</Text>
            <Text style={styles.symptomTxt}>{data.symptom}</Text>
          </View>
        ) : null}

        {data.insights && data.insights.length > 0 && (
          <View style={styles.insightWrap}>
            <Text style={styles.sectionLabel}>AI 인사이트</Text>
            {data.insights.map(item => (
              <InsightCard
                key={item.id}
                severity={item.severity}
                message={item.title}
                detail={item.description}
              />
            ))}
          </View>
        )}

        <PrescriptionImage url={data.imageUrl} onRefresh={refetch} />

        <MemoSection
          memo={data.memo}
          editing={memoEditing}
          text={memoText}
          saving={memoSaving}
          onChangeText={setMemoText}
          onEdit={startMemoEdit}
          onSave={handleMemoSave}
          onCancel={handleMemoCancel}
        />

        <Text style={styles.sectionLabel}>알림 시간</Text>
        <SlotSection
          slots={slots}
          isLoading={slotsLoading}
          onEditSlot={setPickerSlot}
          onAddSlot={() => setAddTodVisible(true)}
          onRemoveSlot={handleRemoveSlot}
        />

        <Text style={styles.sectionLabel}>약 {data.drugs.length}종</Text>
        <View style={styles.drugList}>
          {data.drugs.map((drug, i) => (
            <PrescriptionDrugRow key={`${drug.nameRaw}-${i}`} drug={drug} />
          ))}
          {data.drugs.length === 0 && <Text style={styles.empty}>등록된 약이 없습니다.</Text>}
        </View>

        {data.nutrientNotes && data.nutrientNotes.length > 0 && (
          <>
            <Text style={styles.sectionLabel}>영양소 · 주의</Text>
            <NutrientSection notes={data.nutrientNotes} />
          </>
        )}

        <Pressable
          style={({ pressed }) => [styles.rxDeleteBtn, pressed && styles.rxDeleteBtnPressed]}
          onPress={handleDelete}
          disabled={deleteLoading}
          accessibilityLabel="약봉투 삭제"
          accessibilityRole="button"
        >
          {deleteLoading
            ? <ActivityIndicator size="small" color={colors.statusNegative} />
            : <Text style={styles.rxDeleteBtnTxt}>약봉투 삭제</Text>}
        </Pressable>
      </ScrollView>
    );
  }
}

// ── Detail info card (optional fields from BE) ─────────────────────────────

function DetailInfoCard({ data, onEditPeriod }: { data: PrescriptionDetailView; onEditPeriod: () => void }) {
  const hasInfo = !!(data.status || data.periodStart || data.progressRate != null);
  if (!hasInfo) return null;

  const status = data.status ?? 'ONGOING';

  return (
    <View style={styles.infoCard}>
      <View style={styles.infoTopRow}>
        <StatusChip status={status} />
      </View>
      {(data.periodStart || data.daysRemaining != null || data.adherenceRate != null) && (
        <Pressable
          style={styles.infoPeriodRow}
          onPress={onEditPeriod}
          accessibilityLabel="복약 기간 수정"
          accessibilityRole="button"
        >
          <Text style={styles.infoPeriodTxt} numberOfLines={1}>{buildPeriodText(data)}</Text>
          <Feather name="edit-2" size={scale(12)} color={colors.labelAssistive} />
          <DayBadge data={data} />
        </Pressable>
      )}
      {data.progressRate != null && <ProgressBar rate={data.progressRate} />}
    </View>
  );
}

function StatusChip({ status }: { status: 'ONGOING' | 'COMPLETED' }) {
  const isOngoing = status === 'ONGOING';
  return (
    <View style={[styles.chip, isOngoing ? styles.chipOngoing : styles.chipCompleted]}>
      <Text style={[styles.chipTxt, isOngoing ? styles.chipTxtOngoing : styles.chipTxtCompleted]}>
        {isOngoing ? '복용중' : '복용완료'}
      </Text>
    </View>
  );
}

function DayBadge({ data }: { data: PrescriptionDetailView }) {
  const status = data.status ?? 'ONGOING';
  if (status === 'COMPLETED') {
    const pct = data.adherenceRate != null ? `${Math.round(data.adherenceRate * 100)}%` : '—';
    return <Text style={styles.adherenceTxt}>복약률 {pct}</Text>;
  }
  const d = data.daysRemaining ?? null;
  if (d == null) return null;
  if (d === 0) return <Text style={[styles.dDayTxt, styles.dDayUrgent]}>오늘 마지막</Text>;
  if (d === 1) return <Text style={[styles.dDayTxt, styles.dDayCautionary]}>내일 마지막</Text>;
  return <Text style={styles.dDayTxt}>D-{d}</Text>;
}

function ProgressBar({ rate }: { rate: number }) {
  const clamped = Math.min(1, Math.max(0, rate));
  return (
    <View style={styles.progressTrack}>
      {clamped > 0 && <View style={[styles.progressFill, { flex: clamped }]} />}
      <View style={{ flex: Math.max(0.001, 1 - clamped) }} />
    </View>
  );
}

function buildPeriodText(data: PrescriptionDetailView): string {
  const start = data.periodStart ?? null;
  const end   = data.periodEnd   ?? null;
  if (!start || !end) return '기간 미지정';
  const days = Math.round((new Date(end).getTime() - new Date(start).getTime()) / 86400000) + 1;
  return `${days}일분 · ${shortDate(start)} → ${shortDate(end)}`;
}

function shortDate(dateStr: string): string {
  const [, m, d] = dateStr.slice(0, 10).split('-');
  return `${parseInt(m, 10)}.${parseInt(d, 10)}`;
}

function formatPrescribedAt(dateStr: string): string {
  const [y, m, d] = dateStr.slice(0, 10).split('-');
  return `${y}.${m}.${d}`;
}

// ── Label header ────────────────────────────────────────────────────────────

interface LabelHeaderProps {
  label?: string | null;
  prescribedAt: string;
  editing: boolean;
  text: string;
  saving: boolean;
  onChangeText: (t: string) => void;
  onEdit: () => void;
  onSave: () => void;
  onCancel: () => void;
}

function LabelHeader({ label, prescribedAt, editing, text, saving, onChangeText, onEdit, onSave, onCancel }: LabelHeaderProps) {
  if (editing) {
    return (
      <View style={styles.labelEditCard}>
        <TextInput
          style={styles.labelInput}
          value={text}
          onChangeText={onChangeText}
          placeholder="약봉투 이름 입력"
          placeholderTextColor={colors.labelAssistive}
          maxLength={50}
          autoFocus
          returnKeyType="done"
          onSubmitEditing={onSave}
          accessibilityLabel="약봉투 이름 입력"
        />
        <View style={styles.labelEditActions}>
          <Pressable style={styles.labelCancelBtn} onPress={onCancel} accessibilityRole="button">
            <Text style={styles.labelCancelTxt}>취소</Text>
          </Pressable>
          <Pressable
            style={[styles.labelSaveBtn, saving && styles.labelSaveBtnDisabled]}
            onPress={onSave}
            disabled={saving}
            accessibilityRole="button"
            accessibilityLabel="이름 저장"
          >
            <Text style={styles.labelSaveTxt}>{saving ? '저장 중…' : '저장'}</Text>
          </Pressable>
        </View>
      </View>
    );
  }

  return (
    <Pressable
      style={styles.labelHeader}
      onPress={onEdit}
      accessibilityLabel={label ? '약봉투 이름 편집' : '약봉투 이름 추가'}
      accessibilityRole="button"
    >
      <Text style={styles.labelHeaderDate}>{formatPrescribedAt(prescribedAt)}</Text>
      <View style={styles.labelRow}>
        {label ? (
          <Text style={styles.labelHeaderText} numberOfLines={2}>{label}</Text>
        ) : (
          <Text style={styles.labelHeaderPlaceholder}>약봉투 이름 추가</Text>
        )}
        <Feather name="edit-2" size={scale(16)} color={colors.labelAssistive} />
      </View>
    </Pressable>
  );
}

// ── Memo section ────────────────────────────────────────────────────────────

interface MemoSectionProps {
  memo: string | null | undefined;
  editing: boolean;
  text: string;
  saving: boolean;
  onChangeText: (t: string) => void;
  onEdit: () => void;
  onSave: () => void;
  onCancel: () => void;
}

function MemoSection({ memo, editing, text, saving, onChangeText, onEdit, onSave, onCancel }: MemoSectionProps) {
  if (editing) {
    return (
      <View style={styles.memoEditCard}>
        <TextInput
          style={styles.memoInput}
          value={text}
          onChangeText={onChangeText}
          placeholder="메모를 입력하세요"
          placeholderTextColor={colors.labelAssistive}
          multiline
          textAlignVertical="top"
          maxLength={500}
          autoFocus
          accessibilityLabel="메모 입력"
        />
        <View style={styles.memoActions}>
          <Pressable style={styles.memoCancelBtn} onPress={onCancel} accessibilityRole="button">
            <Text style={styles.memoCancelTxt}>취소</Text>
          </Pressable>
          <Pressable
            style={[styles.memoSaveBtn, saving && styles.memoSaveBtnDisabled]}
            onPress={onSave}
            disabled={saving}
            accessibilityRole="button"
            accessibilityLabel="메모 저장"
          >
            <Text style={styles.memoSaveTxt}>{saving ? '저장 중…' : '저장'}</Text>
          </Pressable>
        </View>
      </View>
    );
  }

  if (memo) {
    return (
      <Pressable
        style={styles.memoBox}
        onPress={onEdit}
        accessibilityLabel="메모 편집"
        accessibilityRole="button"
      >
        <View style={styles.memoBoxRow}>
          <Feather name="edit-2" size={scale(11)} color={colors.yellow40} />
          <Text style={styles.memoTxt} numberOfLines={4}>{memo}</Text>
        </View>
        <Text style={styles.memoEditHint}>탭하여 편집</Text>
      </Pressable>
    );
  }

  return (
    <Pressable
      style={styles.memoPlaceholder}
      onPress={onEdit}
      accessibilityLabel="메모 추가"
      accessibilityRole="button"
    >
      <Feather name="plus" size={scale(14)} color={colors.labelAssistive} />
      <Text style={styles.memoPlaceholderTxt}>메모 추가</Text>
    </Pressable>
  );
}

// ── Slot section ────────────────────────────────────────────────────────────

interface SlotSectionProps {
  slots: SlotEditView[];
  isLoading: boolean;
  onEditSlot: (slot: SlotEditView) => void;
  onAddSlot: () => void;
  onRemoveSlot: (slot: SlotEditView) => void;
}

function SlotSection({ slots, isLoading, onEditSlot, onAddSlot, onRemoveSlot }: SlotSectionProps) {
  if (isLoading) {
    return (
      <View style={styles.slotCard}>
        <ActivityIndicator size="small" color={colors.primaryBase} style={{ padding: space.s16 }} />
      </View>
    );
  }
  return (
    <View style={styles.slotCard}>
      {slots.length === 0 ? (
        <Text style={styles.slotEmpty}>등록된 알림 시간이 없습니다.</Text>
      ) : (
        slots.map((slot, i) => (
          <SlotRow
            key={slot.scheduleId}
            slot={slot}
            isFirst={i === 0}
            onEdit={onEditSlot}
            onRemove={onRemoveSlot}
          />
        ))
      )}
      <AddSlotButton onPress={onAddSlot} hasBorderTop={slots.length > 0} />
    </View>
  );
}

interface SlotRowProps {
  slot: SlotEditView;
  isFirst: boolean;
  onEdit: (slot: SlotEditView) => void;
  onRemove: (slot: SlotEditView) => void;
}

function SlotRow({ slot, isFirst, onEdit, onRemove }: SlotRowProps) {
  const label = TIME_OF_DAY_LABEL[slot.timeOfDay] ?? slot.timeOfDay;

  return (
    <View style={[styles.slotRow, !isFirst && styles.slotBorderTop]}>
      <View style={styles.slotLeft}>
        <Text style={[styles.slotTime, !slot.editable && styles.slotTimeMuted]}>
          {formatTimeHHmm(
            Number(slot.time.split(':')[0]),
            Number(slot.time.split(':')[1]),
          )}
        </Text>
        <Text style={[styles.slotDayLabel, !slot.editable && styles.slotTimeMuted]}>
          {label}
        </Text>
      </View>

      <View style={styles.slotActions}>
        <Pressable
          onPress={() => onRemove(slot)}
          style={styles.deleteBtn}
          accessibilityLabel={`${label} 알림 삭제`}
          accessibilityRole="button"
          hitSlop={8}
        >
          <Feather name="trash-2" size={scale(14)} color={colors.labelAssistive} />
        </Pressable>

        {slot.editable ? (
          <Pressable
            style={styles.editBtn}
            onPress={() => onEdit(slot)}
            accessibilityLabel={`${label} 알림시간 변경`}
            accessibilityRole="button"
          >
            <Feather name="edit-2" size={scale(14)} color={colors.primaryNormal} />
            <Text style={styles.editBtnTxt}>변경</Text>
          </Pressable>
        ) : (
          <View style={styles.lockedBadge}>
            <Feather name="lock" size={scale(13)} color={colors.labelAssistive} />
            <Text style={styles.lockedTxt}>종료</Text>
          </View>
        )}
      </View>
    </View>
  );
}

function AddSlotButton({ onPress, hasBorderTop }: { onPress: () => void; hasBorderTop: boolean }) {
  return (
    <Pressable
      style={[styles.addSlotBtn, hasBorderTop && styles.addSlotBtnBorder]}
      onPress={onPress}
      accessibilityLabel="알림 시간 추가"
      accessibilityRole="button"
    >
      <Feather name="plus" size={scale(14)} color={colors.primaryNormal} />
      <Text style={styles.addSlotBtnTxt}>알림 시간 추가</Text>
    </Pressable>
  );
}

// ── Image ───────────────────────────────────────────────────────────────────

function PrescriptionImage({ url, onRefresh }: { url: string | null; onRefresh: () => void }) {
  const [failed, setFailed] = useState(false);
  if (!url || failed) {
    return (
      <Pressable style={styles.imagePlaceholder} onPress={onRefresh} accessibilityLabel="약봉투 이미지 다시 불러오기">
        <Feather name="image" size={scale(32)} color={colors.labelAssistive} />
        <Text style={styles.placeholderText}>
          {url ? '이미지를 불러올 수 없어요 · 탭하여 새로고침' : '등록된 이미지가 없어요'}
        </Text>
      </Pressable>
    );
  }
  return (
    <ExpoImage
      source={{ uri: url }}
      style={styles.image}
      contentFit="cover"
      cachePolicy="memory-disk"
      placeholder={{ blurhash: 'L6Pj0^jE.AyE_3t7t7R**0o#DgR4' }}
      accessibilityLabel="약봉투 이미지"
      onError={() => setFailed(true)}
    />
  );
}

// ── Nutrient section ────────────────────────────────────────────────────────

function NutrientSection({ notes }: { notes: NutrientNote[] }) {
  return (
    <View style={styles.nutrientCard}>
      {notes.map((note, i) => (
        <View key={i} style={[styles.nutrientItem, i > 0 && styles.nutrientBorderTop]}>
          <Text style={styles.nutrientName}>{note.nutrientName}</Text>
          <Text style={styles.nutrientAdvice}>{note.advice}</Text>
          <Text style={styles.nutrientSource}>출처: {note.source}</Text>
        </View>
      ))}
      <View style={styles.nutrientDisclaimer}>
        <Text style={styles.nutrientDisclaimerTxt}>
          일반 정보예요. 보충 전 약사·의사와 상담하세요.
        </Text>
      </View>
    </View>
  );
}

// ── Header / Error ──────────────────────────────────────────────────────────

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
      <Text style={styles.headerTitle}>약봉투 상세</Text>
      <View style={styles.headerSpacer} />
    </View>
  );
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <View style={styles.errorBox}>
      <Text style={styles.errorText}>약봉투를 불러올 수 없어요</Text>
      <Pressable style={styles.retryBtn} onPress={onRetry} accessibilityLabel="다시 시도" accessibilityRole="button">
        <Text style={styles.retryText}>다시 시도</Text>
      </Pressable>
    </View>
  );
}

// ── Styles ──────────────────────────────────────────────────────────────────

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

  // Label header
  labelHeader: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, padding: space.s16,
    gap: space.s4, ...shadows.small,
  },
  labelHeaderDate: { ...typography.caption1, color: colors.labelAlternative },
  labelRow: { flexDirection: 'row', alignItems: 'center', gap: space.s8 },
  labelHeaderText: { flex: 1, fontSize: scale(20), fontWeight: '700', color: colors.labelNormal },
  labelHeaderPlaceholder: { flex: 1, fontSize: scale(16), color: colors.labelAssistive },
  labelEditCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.primaryNormal, overflow: 'hidden', ...shadows.small,
  },
  labelInput: {
    fontSize: scale(16), fontWeight: '600', color: colors.labelNormal,
    padding: space.s16, minHeight: scale(60),
  },
  labelEditActions: {
    flexDirection: 'row', justifyContent: 'flex-end', gap: space.s8,
    borderTopWidth: 1, borderTopColor: colors.line, padding: space.s10,
  },
  labelCancelBtn: { paddingVertical: space.s8, paddingHorizontal: space.s16, borderRadius: radius.r10 },
  labelCancelTxt: { fontSize: scale(13), color: colors.labelAlternative },
  labelSaveBtn: {
    paddingVertical: space.s8, paddingHorizontal: space.s16,
    borderRadius: radius.r10, backgroundColor: colors.primaryNormal,
  },
  labelSaveBtnDisabled: { opacity: 0.5 },
  labelSaveTxt: { fontSize: scale(13), fontWeight: '600', color: colors.staticWhite },

  image: { width: '100%', height: scale(220), borderRadius: radius.r16, backgroundColor: colors.fillNormal },
  imagePlaceholder: {
    width: '100%', height: scale(160), borderRadius: radius.r16, gap: space.s8,
    backgroundColor: colors.fillNormal, alignItems: 'center', justifyContent: 'center',
  },
  placeholderText: { fontSize: scale(13), color: colors.labelAlternative },
  sectionLabel: { fontSize: scale(13), fontWeight: '700', color: colors.labelAlternative },
  drugList: { gap: space.s8 },
  empty: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center', paddingVertical: space.s20 },
  rxDeleteBtn: {
    marginTop: space.s8, paddingVertical: space.s14, borderRadius: radius.r12,
    borderWidth: 1, borderColor: colors.statusNegative,
    alignItems: 'center', justifyContent: 'center',
  },
  rxDeleteBtnPressed: { backgroundColor: 'rgba(255,59,48,0.06)' },
  rxDeleteBtnTxt: { ...typography.label1n, color: colors.statusNegative, fontWeight: '600' },
  errorBox: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space.s12, padding: space.s16 },
  errorText: { fontSize: scale(14), color: colors.labelAlternative },
  retryBtn: { paddingHorizontal: space.s20, paddingVertical: space.s12, borderRadius: radius.r12, backgroundColor: colors.primaryNormal },
  retryText: { fontSize: scale(14), fontWeight: '600', color: colors.staticWhite },

  // Detail info card
  infoCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, padding: space.s16,
    gap: space.s10, ...shadows.small,
  },
  infoTopRow: { flexDirection: 'row', alignItems: 'center', gap: space.s10 },
  infoPeriodRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space.s8,
  },
  infoPeriodTxt: { flex: 1, fontSize: scale(12), color: colors.labelAlternative },
  chip: { paddingHorizontal: space.s10, paddingVertical: scale(3), borderRadius: radius.r8, borderWidth: 1 },
  chipOngoing:   { borderColor: colors.statusPositive, backgroundColor: 'transparent' },
  chipCompleted: { borderColor: colors.line, backgroundColor: colors.bgAlt },
  chipTxt: { fontSize: scale(11), fontWeight: '700' },
  chipTxtOngoing:   { color: colors.statusPositive },
  chipTxtCompleted: { color: colors.labelAlternative },
  adherenceTxt: { fontSize: scale(12), color: colors.labelAlternative },
  dDayTxt: { fontSize: scale(12), fontWeight: '700', color: colors.labelNormal },
  dDayUrgent:    { color: colors.statusCautionary },
  dDayCautionary:{ color: colors.statusCautionary },
  progressTrack: {
    height: scale(4), backgroundColor: colors.bgAlt, borderRadius: radius.r4,
    overflow: 'hidden', flexDirection: 'row',
  },
  progressFill: { height: '100%', backgroundColor: colors.primaryNormal },

  // Memo section
  memoBox: {
    backgroundColor: colors.yellow95, borderRadius: radius.r12,
    borderWidth: 1, borderColor: '#FDE68A', padding: space.s14, gap: space.s6,
  },
  memoBoxRow: { flexDirection: 'row', alignItems: 'flex-start', gap: space.s6 },
  memoTxt: { flex: 1, fontSize: scale(13), color: colors.yellow40, lineHeight: scale(18) },
  memoEditHint: { fontSize: scale(11), color: colors.yellow40, opacity: 0.6, textAlign: 'right' },
  memoPlaceholder: {
    flexDirection: 'row', alignItems: 'center', gap: space.s8,
    paddingVertical: space.s12, paddingHorizontal: space.s14,
    borderRadius: radius.r12, borderWidth: 1, borderColor: colors.line,
    borderStyle: 'dashed',
  },
  memoPlaceholderTxt: { fontSize: scale(13), color: colors.labelAssistive },
  memoEditCard: {
    backgroundColor: colors.yellow95, borderRadius: radius.r12,
    borderWidth: 1, borderColor: '#FDE68A', overflow: 'hidden',
  },
  memoInput: {
    fontSize: scale(13), color: colors.labelNormal, padding: space.s14,
    minHeight: scale(80), textAlignVertical: 'top',
  },
  memoActions: {
    flexDirection: 'row', justifyContent: 'flex-end', gap: space.s8,
    borderTopWidth: 1, borderTopColor: '#FDE68A', padding: space.s10,
  },
  memoCancelBtn: { paddingVertical: space.s8, paddingHorizontal: space.s16, borderRadius: radius.r10 },
  memoCancelTxt: { fontSize: scale(13), color: colors.labelAlternative },
  memoSaveBtn: {
    paddingVertical: space.s8, paddingHorizontal: space.s16,
    borderRadius: radius.r10, backgroundColor: colors.primaryNormal,
  },
  memoSaveBtnDisabled: { opacity: 0.5 },
  memoSaveTxt: { fontSize: scale(13), fontWeight: '600', color: colors.staticWhite },

  // Slot section
  slotCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, overflow: 'hidden', ...shadows.small,
  },
  slotRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingVertical: space.s14, paddingHorizontal: space.s16,
  },
  slotBorderTop: { borderTopWidth: 1, borderTopColor: colors.line },
  slotLeft: { gap: 2 },
  slotActions: { flexDirection: 'row', alignItems: 'center', gap: space.s8 },
  slotTime: { fontSize: scale(18), fontWeight: '700', color: colors.labelNormal, letterSpacing: -0.3 },
  slotTimeMuted: { color: colors.labelAssistive },
  slotDayLabel: { fontSize: scale(12), color: colors.labelAlternative },
  slotEmpty: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center', padding: space.s20 },
  deleteBtn: { padding: space.s6, borderRadius: radius.r8 },
  editBtn: {
    flexDirection: 'row', alignItems: 'center', gap: space.s4,
    paddingVertical: space.s8, paddingHorizontal: space.s14,
    borderRadius: radius.r12, backgroundColor: colors.blue95, borderWidth: 1, borderColor: colors.primaryNormal,
  },
  editBtnTxt: { fontSize: scale(13), fontWeight: '600', color: colors.primaryNormal },
  lockedBadge: {
    flexDirection: 'row', alignItems: 'center', gap: space.s4,
    paddingVertical: space.s8, paddingHorizontal: space.s12,
    borderRadius: radius.r12, backgroundColor: colors.fillNormal,
  },
  lockedTxt: { fontSize: scale(12), color: colors.labelAssistive },
  addSlotBtn: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.s4,
    paddingVertical: space.s12, paddingHorizontal: space.s16,
  },
  addSlotBtnBorder: { borderTopWidth: 1, borderTopColor: colors.line },
  addSlotBtnTxt: { fontSize: scale(13), fontWeight: '600', color: colors.primaryNormal },

  // ToD modal
  modalBackdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.45)' },
  todSheet: {
    position: 'absolute', bottom: 0, left: 0, right: 0,
    backgroundColor: colors.bgNormal,
    borderTopLeftRadius: radius.r20, borderTopRightRadius: radius.r20,
    paddingTop: space.s24, paddingBottom: space.s40, paddingHorizontal: space.s24, gap: space.s4,
  },
  todSheetTitle: {
    fontSize: scale(17), fontWeight: '700', color: colors.labelNormal,
    textAlign: 'center', marginBottom: space.s16,
  },
  todOption: {
    paddingVertical: space.s16, borderBottomWidth: 1, borderBottomColor: colors.line, alignItems: 'center',
  },
  todOptionTxt: { fontSize: scale(15), fontWeight: '600', color: colors.labelNormal },
  todCancelBtn: { paddingVertical: space.s16, alignItems: 'center', marginTop: space.s8 },
  todCancelTxt: { fontSize: scale(15), color: colors.labelAlternative },

  // Toast
  toast: {
    position: 'absolute', bottom: space.s32, alignSelf: 'center',
    backgroundColor: 'rgba(23,23,25,0.88)', borderRadius: radius.r20,
    paddingHorizontal: space.s20, paddingVertical: space.s12,
    maxWidth: '85%',
  },
  toastTxt: { ...typography.label2, color: colors.bgNormal, fontWeight: '600', textAlign: 'center' },

  // Symptom row
  symptomRow: {
    flexDirection: 'row', alignItems: 'flex-start', gap: space.s8,
    backgroundColor: colors.bgNormal, borderRadius: radius.r12,
    borderWidth: 1, borderColor: colors.line,
    paddingHorizontal: space.s14, paddingVertical: space.s10,
  },
  symptomLabel: { fontSize: scale(12), fontWeight: '700', color: colors.labelAlternative, paddingTop: 1 },
  symptomTxt: { flex: 1, fontSize: scale(13), color: colors.labelNormal, lineHeight: scale(18) },

  // AI insight
  insightWrap: { gap: space.s8 },

  // Nutrient section
  nutrientCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, overflow: 'hidden', ...shadows.small,
  },
  nutrientItem: { paddingHorizontal: space.s16, paddingVertical: space.s14, gap: space.s4 },
  nutrientBorderTop: { borderTopWidth: 1, borderTopColor: colors.line },
  nutrientName: { fontSize: scale(14), fontWeight: '700', color: colors.labelNormal },
  nutrientAdvice: { fontSize: scale(13), color: colors.labelAlternative, lineHeight: scale(18) },
  nutrientSource: { fontSize: scale(11), color: colors.labelAssistive },
  nutrientDisclaimer: {
    borderTopWidth: 1, borderTopColor: colors.line,
    backgroundColor: '#F0F7FF', paddingHorizontal: space.s16, paddingVertical: space.s10,
  },
  nutrientDisclaimerTxt: {
    fontSize: scale(11), color: colors.labelAlternative, textAlign: 'center', lineHeight: scale(16),
  },

  // Period sheet
  periodSheet: {
    position: 'absolute', bottom: 0, left: 0, right: 0,
    backgroundColor: colors.bgNormal,
    borderTopLeftRadius: radius.r20, borderTopRightRadius: radius.r20,
    paddingTop: space.s24, paddingBottom: space.s40, paddingHorizontal: space.s24, gap: space.s4,
  },
  periodSheetTitle: {
    fontSize: scale(17), fontWeight: '700', color: colors.labelNormal,
    textAlign: 'center', marginBottom: space.s4,
  },
  periodSheetHint: {
    fontSize: scale(12), color: colors.labelAlternative,
    textAlign: 'center', lineHeight: scale(18), marginBottom: space.s8,
  },
  periodOption: {
    paddingVertical: space.s16, borderBottomWidth: 1, borderBottomColor: colors.line, alignItems: 'center',
  },
  periodOptionTxt: { fontSize: scale(15), fontWeight: '600', color: colors.primaryNormal },
});
