import React from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, shadows } from '@/styles/tokens';

interface FabButtonProps {
  onPress: () => void;
}

function FabButton({ onPress }: FabButtonProps) {
  return (
    <View style={styles.wrapper}>
      <Pressable
        onPress={onPress}
        style={({ pressed }) => [styles.fab, pressed && styles.fabPressed]}
        accessibilityLabel="처방전 등록"
        accessibilityRole="button"
        accessibilityHint="처방전 등록 화면으로 이동합니다"
      >
        <Ionicons name="add" size={30} color="#fff" />
      </Pressable>
    </View>
  );
}

export default React.memo(FabButton);

const styles = StyleSheet.create({
  wrapper: {
    width: 80,
    alignItems: 'center',
  },
  fab: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.primaryBase,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 4,
    borderColor: colors.bgNormal,
    ...shadows.fab,
  },
  fabPressed: {
    opacity: 0.85,
    transform: [{ scale: 0.96 }],
  },
});
