import React, { useCallback } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import * as Haptics from 'expo-haptics';
import Icon from '@/components/common/Icon';
import PillVisual from '@/components/common/PillVisual';
import { scale, colors, space, radius, shadows } from '@/styles/tokens';

export type SlotState = 'done' | 'now' | 'wait';

export interface TimeSlot {
  id: string;
  label: string;
  time: string;
  state: SlotState;
  drugCount: number;
  pillColors: string[];
  doseLogId?: number;
  doseLogIds: number[];
  prescriptionId?: number;
  prescriptionName?: string;
}

interface TimeSlotCardsProps {
  slots: TimeSlot[];
  onSlotPress?: (slot: TimeSlot) => void;
  onPrescriptionPress?: (slot: TimeSlot) => void;
}

const CARD_STYLE: Record<SlotState, object> = {
  done: { backgroundColor: colors.bgNormal, borderWidth: 1.5, borderColor: colors.green90, ...shadows.timeSlotDone },
  now:  { backgroundColor: colors.bgAlt,    borderWidth: 1.5, borderColor: colors.primaryNormal, ...shadows.timeSlotNow },
  wait: { backgroundColor: colors.bgAlt,    borderWidth: 1,   borderColor: colors.line },
};

const TIME_COLOR: Record<SlotState, string> = {
  done: colors.green40,
  now:  colors.primaryNormal,
  wait: colors.labelAssistive,
};

const NAME_COLOR: Record<SlotState, string> = {
  done: colors.labelAlternative,
  now:  colors.labelNormal,
  wait: colors.labelNeutral,
};

const FALLBACK_TEXT: Record<SlotState, string> = {
  done: '복용 완료',
  now:  '지금 드세요',
  wait: '복용 대기',
};

interface SlotItemProps {
  slot: TimeSlot;
  onCheckPress: (slot: TimeSlot) => void;
  onPrescriptionPress?: (slot: TimeSlot) => void;
}

function SlotItem({ slot, onCheckPress, onPrescriptionPress }: SlotItemProps) {
  const handleCheckPress = useCallback(() => onCheckPress(slot), [onCheckPress, slot]);
  const handlePrescriptionPress = useCallback(
    () => onPrescriptionPress?.(slot),
    [onPrescriptionPress, slot],
  );
  const isDone = slot.state === 'done';
  const canNavigate = !!slot.prescriptionId && !!onPrescriptionPress;

  return (
    <View style={[styles.card, CARD_STYLE[slot.state]]}>
      <Pressable
        style={[styles.checkbox, isDone && styles.checkboxDone]}
        onPress={handleCheckPress}
        accessibilityLabel={`${slot.label} ${slot.time} 복약 체크`}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: isDone }}
      >
        {isDone && <Icon name="check" size={scale(20)} color="#fff" />}
      </Pressable>

      <View style={styles.textArea}>
        <Text style={[styles.timeLabel, { color: TIME_COLOR[slot.state] }]}>
          {slot.time}
        </Text>
        {canNavigate ? (
          <Pressable onPress={handlePrescriptionPress} accessibilityRole="link">
            <Text
              style={[styles.prescriptionName, { color: NAME_COLOR[slot.state] }]}
              numberOfLines={1}
            >
              {slot.prescriptionName}
            </Text>
          </Pressable>
        ) : (
          <Text style={[styles.prescriptionName, { color: NAME_COLOR[slot.state] }]}>
            {slot.prescriptionName ?? FALLBACK_TEXT[slot.state]}
          </Text>
        )}
      </View>

      <PillVisual
        size={scale(32)}
        colorA={slot.pillColors[0] ?? '#aaa'}
        colorB={slot.pillColors[1]}
        dimmed={!isDone}
      />
    </View>
  );
}

const MemoSlotItem = React.memo(SlotItem);

function TimeSlotCards({ slots, onSlotPress, onPrescriptionPress }: TimeSlotCardsProps) {
  const handleCheckPress = useCallback(async (slot: TimeSlot) => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    onSlotPress?.(slot);
  }, [onSlotPress]);

  return (
    <View style={styles.list}>
      {slots.map((slot) => (
        <MemoSlotItem
          key={slot.id}
          slot={slot}
          onCheckPress={handleCheckPress}
          onPrescriptionPress={onPrescriptionPress}
        />
      ))}
    </View>
  );
}

export default React.memo(TimeSlotCards);

const styles = StyleSheet.create({
  list: {
    gap: space.s10,
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.s12,
    borderRadius: radius.r16,
    padding: space.s16,
  },
  checkbox: {
    width: scale(40),
    height: scale(40),
    borderRadius: radius.full,
    borderWidth: 2,
    borderColor: colors.labelAssistive,
    backgroundColor: colors.bgNormal,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkboxDone: {
    backgroundColor: colors.statusPositive,
    borderColor: colors.statusPositive,
  },
  textArea: {
    flex: 1,
    gap: 2,
  },
  timeLabel: {
    fontSize: scale(11),
    fontWeight: '700',
    letterSpacing: 0.3,
    textTransform: 'uppercase',
  },
  prescriptionName: {
    fontSize: scale(16),
    fontWeight: '700',
    lineHeight: scale(22),
  },
});
