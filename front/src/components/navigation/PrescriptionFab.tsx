import React, { useCallback } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { router, useSegments } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, shadows } from '@/styles/tokens';

const BAR_H = 56;
const FAB_D = 56;

// 그룹 탭은 자체 FAB (새 그룹 만들기) 가 있으므로 충돌 회피 — 단일 FAB 정책
const SCREENS_WITHOUT_PRESCRIPTION_FAB = new Set(['group']);

function PrescriptionFab() {
  const insets = useSafeAreaInsets();
  const segments = useSegments();
  const currentTab = segments[segments.length - 1] as string | undefined;
  if (currentTab && SCREENS_WITHOUT_PRESCRIPTION_FAB.has(currentTab)) return null;

  const handlePress = useCallback(async () => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    router.push('/prescription' as any);
  }, []);

  const fabBottom = insets.bottom + BAR_H - FAB_D / 2;

  return (
    <View
      style={[styles.wrapper, { bottom: fabBottom }]}
      pointerEvents="box-none"
    >
      {/* Wrap in View so backgroundColor renders reliably; Pressable handles touch */}
      <Pressable
        onPress={handlePress}
        accessibilityLabel="처방전 등록"
        accessibilityRole="button"
        accessibilityHint="처방전 등록 화면으로 이동합니다"
      >
        {({ pressed }) => (
          <View style={[styles.fab, pressed && styles.fabPressed]}>
            <Ionicons name="add" size={30} color="#fff" />
          </View>
        )}
      </Pressable>
    </View>
  );
}

export default React.memo(PrescriptionFab);

const styles = StyleSheet.create({
  wrapper: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
    zIndex: 100,
  },
  fab: {
    width: FAB_D,
    height: FAB_D,
    borderRadius: FAB_D / 2,
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
