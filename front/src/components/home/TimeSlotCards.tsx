import React, { useCallback } from 'react';
import { View, Text, ScrollView, Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, typography, space, radius, shadows } from '@/styles/tokens';

export type SlotStatus = 'done' | 'current' | 'pending';

export interface TimeSlot {
  id: string;
  label: string;
  time: string;
  drugCount: number;
  status: SlotStatus;
}

interface TimeSlotCardsProps {
  slots: TimeSlot[];
  onSlotPress?: (slot: TimeSlot) => void;
}

const STATUS_COLORS: Record<SlotStatus, string> = {
  done: '#EDF7EF',
  current: '#EEF4FF',
  pending: colors.bgAlt,
};

const STATUS_ICON_COLOR: Record<SlotStatus, string> = {
  done: colors.statusPositive,
  current: colors.primaryNormal,
  pending: colors.labelAlternative,
};

const STATUS_LABEL: Record<SlotStatus, string> = {
  done: '완료',
  current: '복용시간',
  pending: '대기',
};

interface SlotCardProps {
  slot: TimeSlot;
  onPress: (slot: TimeSlot) => void;
}

function SlotCard({ slot, onPress }: SlotCardProps) {
  const handlePress = useCallback(() => onPress(slot), [onPress, slot]);
  const bgColor = STATUS_COLORS[slot.status];
  const iconColor = STATUS_ICON_COLOR[slot.status];

  return (
    <Pressable
      style={[styles.card, { backgroundColor: bgColor }, slot.status === 'current' && styles.cardCurrent]}
      onPress={handlePress}
      accessibilityLabel={`${slot.label} ${slot.time} ${slot.drugCount}개 ${STATUS_LABEL[slot.status]}`}
      accessibilityRole="button"
    >
      <View style={styles.iconRow}>
        {slot.status === 'done' ? (
          <Ionicons name="checkmark-circle" size={24} color={iconColor} />
        ) : slot.status === 'current' ? (
          <Ionicons name="ellipse" size={14} color={iconColor} />
        ) : (
          <Ionicons name="ellipse-outline" size={14} color={iconColor} />
        )}
        <Text style={[styles.statusLabel, { color: iconColor }]}>{STATUS_LABEL[slot.status]}</Text>
      </View>
      <Text style={styles.slotLabel}>{slot.label}</Text>
      <Text style={styles.slotTime}>{slot.time}</Text>
      <Text style={styles.drugCount}>💊 {slot.drugCount}개</Text>
    </Pressable>
  );
}

const MemoSlotCard = React.memo(SlotCard);

function TimeSlotCards({ slots, onSlotPress }: TimeSlotCardsProps) {
  const handlePress = useCallback((slot: TimeSlot) => {
    onSlotPress?.(slot);
  }, [onSlotPress]);

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.scrollContent}
    >
      {slots.map((slot) => (
        <MemoSlotCard key={slot.id} slot={slot} onPress={handlePress} />
      ))}
    </ScrollView>
  );
}

export default React.memo(TimeSlotCards);

const styles = StyleSheet.create({
  scrollContent: {
    paddingHorizontal: space.s2,
    gap: space.s10,
    paddingVertical: space.s4,
  },
  card: {
    width: 100,
    borderRadius: radius.r16,
    padding: space.s12,
    gap: space.s4,
    borderWidth: 1,
    borderColor: colors.line,
    ...shadows.small,
  },
  cardCurrent: {
    borderColor: colors.primaryNormal,
    borderWidth: 1.5,
  },
  iconRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.s4,
    marginBottom: space.s2,
  },
  statusLabel: {
    fontSize: 10,
    fontWeight: '600',
  },
  slotLabel: {
    ...typography.label1n,
    color: colors.labelNormal,
    fontWeight: '700',
  },
  slotTime: {
    ...typography.caption1,
    color: colors.labelAlternative,
  },
  drugCount: {
    ...typography.caption1,
    color: colors.labelNeutral,
    marginTop: space.s2,
  },
});
