import React from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { colors } from '@/styles/tokens';

interface FabButtonProps {
  onPress: () => void;
}

function FabButton({ onPress }: FabButtonProps) {
  const handlePress = async () => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    onPress();
  };

  return (
    <View style={styles.container}>
      <Pressable
        onPress={handlePress}
        style={({ pressed }) => [styles.fab, pressed && styles.fabPressed]}
        accessibilityLabel="처방전 등록"
        accessibilityRole="button"
        accessibilityHint="처방전 등록 화면으로 이동합니다"
      >
        <View style={styles.gradient}>
          <Ionicons name="add" size={32} color="#fff" />
        </View>
      </Pressable>
    </View>
  );
}

export default React.memo(FabButton);

const styles = StyleSheet.create({
  container: {
    width: 64,
    height: 64,
    // -8px overflow above tabBar
    marginTop: -8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fab: {
    width: 64,
    height: 64,
    borderRadius: 32,
    shadowColor: colors.primaryNormal,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 8,
    elevation: 8,
  },
  fabPressed: {
    opacity: 0.85,
    transform: [{ scale: 0.96 }],
  },
  gradient: {
    width: 64,
    height: 64,
    borderRadius: 32,
    // Gradient primary blue (primaryNormal → primaryStrong)
    backgroundColor: colors.primaryNormal,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
