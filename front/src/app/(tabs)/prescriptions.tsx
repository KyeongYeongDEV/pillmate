import { View, Pressable, Text, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { router } from "expo-router";
import { colors, typography, space, radius } from "@/styles/tokens";

export default function PrescriptionsScreen() {
  return (
    <SafeAreaView style={styles.root} edges={["top"]}>
      <View style={styles.content}>
        <Text style={styles.title}>처방전</Text>
        <Text style={styles.sub}>등록된 처방전이 아직 없습니다.</Text>
        <Pressable
          style={styles.cta}
          onPress={() => router.push('/prescription' as any)}
          accessibilityLabel="처방전 등록하기"
          accessibilityRole="button"
        >
          <Text style={styles.ctaTxt}>+ 처방전 등록하기</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bgAlt },
  content: { flex: 1, padding: space.s16, gap: space.s12 },
  title: { ...typography.heading1, color: colors.labelNormal },
  sub: { ...typography.body2r, color: colors.labelAlternative },
  cta: {
    backgroundColor: colors.primaryNormal, borderRadius: radius.r16,
    paddingVertical: space.s16, alignItems: 'center', marginTop: space.s8,
  },
  ctaTxt: { ...typography.headline1, color: '#fff' },
});
