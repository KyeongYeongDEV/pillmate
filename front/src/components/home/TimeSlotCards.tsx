import React, { useCallback, useState } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import * as Haptics from 'expo-haptics';
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
  doseLogId?: number;
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

const STATUS_TEXT: Record<SlotState, () => string> = {
  done: () => '복용 완료',
  now:  () => '지금 드세요',
  wait: () => '복용 대기',
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
  const isDone = slot.state === 'done';

  return (
    <Pressable
      style={[styles.card, CARD_STYLE[slot.state]]}
      onPress={handlePress}
      accessibilityLabel={`${slot.label} ${slot.time} 복용 체크`}
      accessibilityRole="button"
    >
      {/* Checkbox — syncs with done state */}
      <View style={[styles.checkbox, isDone && styles.checkboxDone]}>
        {isDone && <Icon name="check" size={20} color="#fff" />}
      </View>

      {/* Text */}
      <View style={styles.textArea}>
        <Text style={[styles.slotLabel, { color: LABEL_COLOR[slot.state] }]}>
          {slot.label} · {slot.time}
        </Text>
        <Text style={[styles.statusText, { color: STATUS_TEXT_COLOR[slot.state] }]}>
          {STATUS_TEXT[slot.state]()}
        </Text>
      </View>

      {/* Pill visual — dimmed when not done */}
      <PillVisual
        size={32}
        colorA={slot.pillColors[0] ?? '#aaa'}
        colorB={slot.pillColors[1]}
        dimmed={!isDone}
      />
    </Pressable>
  );
}

const MemoSlotItem = React.memo(SlotItem);

// State managed internally so the component can self-test toggle and
// Phase 2 will swap this for doseLog API calls.
function TimeSlotCards({ slots: initialSlots, onSlotPress }: TimeSlotCardsProps) {
  const [stateMap, setStateMap] = useState<Record<string, SlotState>>(
    () => Object.fromEntries(initialSlots.map(s => [s.id, s.state])),
  );

  const handlePress = useCallback(async (slot: TimeSlot) => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    const nextState: SlotState = stateMap[slot.id] === 'done' ? 'wait' : 'done';
    setStateMap(prev => ({ ...prev, [slot.id]: nextState }));
    onSlotPress?.({ ...slot, state: nextState });
  }, [stateMap, onSlotPress]);

  const displaySlots = initialSlots.map(s => ({ ...s, state: stateMap[s.id] ?? s.state }));

  return (
    <View style={styles.list}>
      {displaySlots.map((slot) => (
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
