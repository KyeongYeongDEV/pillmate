import React from 'react';
import { View, StyleSheet } from 'react-native';

interface PillVisualProps {
  size?: number;
  colorA: string;
  colorB?: string;
  dimmed?: boolean;
}

// Capsule with two equal halves (left=colorA, right=colorB)
// borderRadius ≈ size×0.31 matches design token shape
function PillVisual({ size = 32, colorA, colorB, dimmed = false }: PillVisualProps) {
  const br = Math.round(size * 0.31);
  const half = size / 2;
  const rightColor = colorB ?? colorA;

  return (
    <View
      style={[
        styles.pill,
        { width: size, height: size, borderRadius: br, opacity: dimmed ? 0.4 : 1 },
      ]}
    >
      <View style={{ width: half, height: size, backgroundColor: colorA }} />
      <View style={{ width: half, height: size, backgroundColor: rightColor }} />
    </View>
  );
}

export default React.memo(PillVisual);

const styles = StyleSheet.create({
  pill: {
    flexDirection: 'row',
    overflow: 'hidden',
  },
});
