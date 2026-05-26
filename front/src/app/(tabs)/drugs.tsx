import { View, Text, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors, typography, space } from '@/styles/tokens';

export default function DrugsScreen() {
  return (
    <SafeAreaView style={styles.root} edges={['top']}>
      <View style={styles.content}>
        <Text style={styles.title}>약</Text>
        <Text style={styles.sub}>약 검색 · 리스트 (Phase 2-FE 구현 예정)</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bgAlt },
  content: { flex: 1, padding: space.s16, alignItems: 'center', justifyContent: 'center', gap: space.s8 },
  title: { ...typography.heading1, color: colors.labelNormal },
  sub: { ...typography.body2r, color: colors.labelAlternative, textAlign: 'center' },
});
