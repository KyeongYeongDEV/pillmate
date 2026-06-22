import { View, Pressable, Text, StyleSheet, FlatList, ActivityIndicator } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { SafeAreaView } from "react-native-safe-area-context";
import { router } from "expo-router";
import { colors, typography, space, radius, scale, shadows } from "@/styles/tokens";
import { useGetPrescriptionsQuery } from "@/store/slices/prescriptionApi";
import PrescriptionListCard from "@/components/prescription/PrescriptionListCard";
import type { PrescriptionSummary } from "@/types/prescription";

export default function PrescriptionsScreen() {
  const { data, isLoading, isError, refetch, isFetching } = useGetPrescriptionsQuery();
  const openDetail = (id: number) => router.push(`/prescription/${id}` as any);

  return (
    <SafeAreaView style={styles.root} edges={["top"]}>
      <View style={styles.header}>
        <Text style={styles.title}>약봉투</Text>
      </View>
      {renderBody()}
      <RegisterFab bottom={space.s24} />
    </SafeAreaView>
  );

  function renderBody() {
    if (isLoading) return <ActivityIndicator size="large" color={colors.primaryBase} style={styles.loader} />;
    if (isError) return <ErrorState onRetry={refetch} />;
    if (!data || data.length === 0) return <EmptyState />;
    return (
      <FlatList
        data={data}
        keyExtractor={keyOf}
        renderItem={({ item }) => <PrescriptionListCard item={item} onPress={openDetail} />}
        contentContainerStyle={styles.list}
        showsVerticalScrollIndicator={false}
        refreshing={isFetching}
        onRefresh={refetch}
      />
    );
  }
}

const keyOf = (p: PrescriptionSummary) => String(p.id);

function RegisterFab({ bottom }: { bottom: number }) {
  const handlePress = async () => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    router.push('/prescription' as any);
  };

  return (
    <View style={[styles.fabWrap, { bottom }]} pointerEvents="box-none">
      <Pressable
        onPress={handlePress}
        accessibilityLabel="약봉투 등록"
        accessibilityRole="button"
        accessibilityHint="약봉투 등록 화면으로 이동합니다"
      >
        {({ pressed }) => (
          <View style={[styles.fab, pressed && styles.fabPressed]}>
            <Ionicons name="add" size={scale(30)} color={colors.staticWhite} />
          </View>
        )}
      </Pressable>
    </View>
  );
}

function EmptyState() {
  return (
    <View style={styles.content}>
      <Text style={styles.sub}>등록된 약봉투가 아직 없습니다.</Text>
      <Pressable
        style={styles.cta}
        onPress={() => router.push('/prescription' as any)}
        accessibilityLabel="약봉투 등록하기"
        accessibilityRole="button"
      >
        <Text style={styles.ctaTxt}>+ 약봉투 등록하기</Text>
      </Pressable>
    </View>
  );
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <View style={styles.content}>
      <Text style={styles.sub}>약봉투를 불러올 수 없습니다.</Text>
      <Pressable style={styles.cta} onPress={onRetry} accessibilityLabel="다시 시도" accessibilityRole="button">
        <Text style={styles.ctaTxt}>다시 시도</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bgAlt },
  header: { paddingHorizontal: space.s16, paddingTop: space.s8, paddingBottom: space.s12 },
  title: { ...typography.heading1, color: colors.labelNormal },
  content: { flex: 1, padding: space.s16, gap: space.s12 },
  list: { padding: space.s16, gap: space.s12 },
  loader: { flex: 1 },
  sub: { ...typography.body2r, color: colors.labelAlternative },
  cta: {
    backgroundColor: colors.primaryNormal, borderRadius: radius.r16,
    paddingVertical: space.s16, alignItems: 'center', marginTop: space.s8,
  },
  ctaTxt: { ...typography.headline1, color: colors.staticWhite },
  fabWrap: {
    position: 'absolute',
    right: space.s16,
    zIndex: 100,
  },
  fab: {
    width: scale(56),
    height: scale(56),
    borderRadius: scale(28),
    backgroundColor: colors.primaryBase,
    alignItems: 'center',
    justifyContent: 'center',
    ...shadows.fab,
  },
  fabPressed: {
    opacity: 0.85,
    transform: [{ scale: 0.96 }],
  },
});
