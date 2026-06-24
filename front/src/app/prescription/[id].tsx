import React, { useCallback, useRef, useState } from 'react';
import {
  View, Text, ScrollView, StyleSheet, Pressable, ActivityIndicator, Image,
  Alert, Animated, Modal,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { useGetPrescriptionDetailQuery } from '@/store/slices/prescriptionApi';
import {
  useGetPrescriptionSlotsQuery,
  useUpdateScheduleTimeMutation,
  useAddPrescriptionSlotMutation,
  useRemovePrescriptionSlotMutation,
} from '@/store/slices/scheduleApi';
import OcrStatusChip from '@/components/prescription/OcrStatusChip';
import PrescriptionDrugRow from '@/components/prescription/PrescriptionDrugRow';
import TimePicker, { formatTimeHHmm } from '@/components/schedule/TimePicker';
import { formatMonthDay } from '@/utils/calendarUtils';
import { safeBack } from '@/lib/router/safeBack';
import { scale, colors, space, radius, typography, shadows } from '@/styles/tokens';
import type { SlotEditView, TimeOfDay } from '@/types/schedule';

const PERIOD_ENDED_CODE = 'PILL_032';
const TOAST_DURATION_MS = 2500;

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
  const [updateTime] = useUpdateScheduleTimeMutation();
  const [addSlot]    = useAddPrescriptionSlotMutation();
  const [removeSlot] = useRemovePrescriptionSlotMutation();

  const [pickerSlot,    setPickerSlot]    = useState<SlotEditView | null>(null);
  const [addTodVisible, setAddTodVisible] = useState(false);
  const [addPickerTod,  setAddPickerTod]  = useState<TimeOfDay | null>(null);
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
      if (code === PERIOD_ENDED_CODE) {
        showToast(err?.data?.error?.message ?? '복약 기간이 종료되어 시간을 수정할 수 없습니다.');
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
      if (code === PERIOD_ENDED_CODE) {
        showToast(err?.data?.error?.message ?? '복약 기간이 종료되어 알림을 추가할 수 없습니다.');
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
      if (code === PERIOD_ENDED_CODE) {
        showToast(err?.data?.error?.message ?? '복약 기간이 종료되어 알림을 삭제할 수 없습니다.');
      } else {
        Alert.alert('오류', '알림 삭제 중 문제가 발생했습니다.');
      }
    }
  }, [removeSlot, prescriptionId, showToast]);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
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
        <View style={styles.metaRow}>
          <Text style={styles.date}>{formatMonthDay(data.prescribedAt)}</Text>
          <OcrStatusChip status={data.ocrStatus} />
        </View>
        <PrescriptionImage url={data.imageUrl} onRefresh={refetch} />

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
      </ScrollView>
    );
  }
}

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
    <Image
      source={{ uri: url }}
      style={styles.image}
      resizeMode="cover"
      accessibilityLabel="약봉투 이미지"
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
});
