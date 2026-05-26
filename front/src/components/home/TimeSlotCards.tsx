import React, { useCallback } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import Icon from '@/components/common/Icon';
import PillVisual from '@/components/common/PillVisual';
import { colors, space, radius, shadows } from '@/styles/tokens';

export type SlotState = 'done' | 'now' | 'wait';

export interface TimeSlot {
  id: string;
  label: string;
  time: string;
  state: SlotState;
  drugCount: number;
  pillColors: string[];
}

interface TimeSlotCardsProps {
  slots: TimeSlot[];
  onSlotPress?: (slot: TimeSlot) => void;
}

const CARD_STYLE: Record<SlotState, object> = {
  done: { backgroundColor: colors.bgNormal, borderWidth: 1.5, borderColor: colors.green90, ...shadows.timeSlotDone },
  now:  { backgroundColor: colors.bgAlt,    borderWidth: 1.5, borderColor: colors.primaryNormal, ...shadows.timeSlotNow },
  wait: { backgroundColor: colors.bgAlt,    borderWidth: 1,   borderColor: colors.line },
};

const LABEL_COLOR: Record<SlotState, string> = {
  done: colors.green40,
  now:  colors.primaryNormal,
  wait: colors.labelAssistive,
};

const STATUS_TEXT: Record<SlotState, (count: number) => string> = {
  done: () => '복용 완료',
  now:  () => '복용 중이에요',
  wait: (n) => `${n}개 예정`,
};

const STATUS_TEXT_COLOR: Record<SlotState, string> = {
  done: colors.green30,
  now:  colors.labelNormal,
  wait: colors.labelNeutral,
};

interface SlotItemProps {
  slot: TimeSlot;
  onPress: (slot: TimeSlot) => void;
}

function SlotItem({ slot, onPress }: SlotItemProps) {
  const handlePress = useCallback(() => onPress(slot), [onPress, slot]);

  return (
    <Pressable
      style={[styles.card, CARD_STYLE[slot.state]]}
      onPress={handlePress}
      accessibilityLabel={`${slot.label} ${slot.time} ${STATUS_TEXT[slot.state](slot.drugCount)}`}
      accessibilityRole="button"
    >
      {/* Checkbox */}
      <View style={[styles.checkbox, slot.state === 'done' && styles.checkboxDone]}>
        {slot.state === 'done' && (
          <Icon name="check" size={20} color="#fff" />
        )}
      </View>

      {/* Text */}
      <View style={styles.textArea}>
        <Text style={[styles.slotLabel, { color: LABEL_COLOR[slot.state] }]}>
          {slot.label} · {slot.time}
        </Text>
        <Text style={[styles.statusText, { color: STATUS_TEXT_COLOR[slot.state] }]}>
          {STATUS_TEXT[slot.state](slot.drugCount)}
        </Text>
      </View>

      {/* Pill visual */}
      <PillVisual
        size={32}
        colorA={slot.pillColors[0] ?? '#aaa'}
        colorB={slot.pillColors[1]}
        dimmed={slot.state === 'wait'}
      />
    </Pressable>
  );
}

const MemoSlotItem = React.memo(SlotItem);

function TimeSlotCards({ slots, onSlotPress }: TimeSlotCardsProps) {
  const handlePress = useCallback((slot: TimeSlot) => onSlotPress?.(slot), [onSlotPress]);

  return (
    <View style={styles.list}>
      {slots.map((slot) => (
        <MemoSlotItem key={slot.id} slot={slot} onPress={handlePress} />
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
    width: 40,
    height: 40,
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
  slotLabel: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.3,
    textTransform: 'uppercase',
  },
  statusText: {
    fontSize: 16,
    fontWeight: '700',
    lineHeight: 22,
  },
});
