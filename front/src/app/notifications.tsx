import { View, Pressable, Text, StyleSheet, FlatList, ActivityIndicator } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { router } from "expo-router";
import { Feather } from "@expo/vector-icons";
import { scale, colors, typography, space, radius } from "@/styles/tokens";
import { useGetNotificationsQuery, useMarkReadMutation } from "@/store/slices/notificationApi";
import { notificationRoute } from "@/lib/notificationMeta";
import NotificationRow from "@/components/notification/NotificationRow";
import type { NotificationItem } from "@/types/notification";
import { safeBack } from "@/lib/router/safeBack";

export default function NotificationsScreen() {
  const { data, isLoading, isError, refetch, isFetching } = useGetNotificationsQuery();
  const [markRead] = useMarkReadMutation();

  const onPress = (item: NotificationItem) => {
    if (item.status !== 'READ') markRead(item.id);
    const route = notificationRoute(item);
    if (route) router.push(route as any);
  };

  return (
    <SafeAreaView style={styles.root} edges={["top"]}>
      <View style={styles.header}>
        <Pressable onPress={() => safeBack('/(tabs)/home')} accessibilityLabel="뒤로" accessibilityRole="button" hitSlop={8}>
          <Feather name="chevron-left" size={scale(24)} color={colors.labelNormal} />
        </Pressable>
        <Text style={styles.title}>알림</Text>
        <View style={styles.headerSpacer} />
      </View>
      {renderBody()}
    </SafeAreaView>
  );

  function renderBody() {
    if (isLoading) return <ActivityIndicator size="large" color={colors.primaryBase} style={styles.loader} />;
    if (isError) return <CenterState text="알림을 불러올 수 없습니다." actionLabel="다시 시도" onPress={refetch} />;
    if (!data || data.length === 0) return <CenterState text="알림이 없습니다." />;
    return (
      <FlatList
        data={data}
        keyExtractor={keyOf}
        renderItem={({ item }) => <NotificationRow item={item} onPress={onPress} />}
        ItemSeparatorComponent={Separator}
        showsVerticalScrollIndicator={false}
        refreshing={isFetching}
        onRefresh={refetch}
      />
    );
  }
}

const keyOf = (n: NotificationItem) => String(n.id);
const Separator = () => <View style={styles.separator} />;

function CenterState({ text, actionLabel, onPress }: { text: string; actionLabel?: string; onPress?: () => void }) {
  return (
    <View style={styles.content}>
      <Text style={styles.sub}>{text}</Text>
      {actionLabel && (
        <Pressable style={styles.cta} onPress={onPress} accessibilityLabel={actionLabel} accessibilityRole="button">
          <Text style={styles.ctaTxt}>{actionLabel}</Text>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bgNormal },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingTop: space.s8, paddingBottom: space.s12,
  },
  title: { ...typography.headline1, color: colors.labelNormal },
  headerSpacer: { width: scale(24) },
  content: { flex: 1, padding: space.s16, gap: space.s12, alignItems: 'center', justifyContent: 'center' },
  loader: { flex: 1 },
  sub: { ...typography.body2r, color: colors.labelAlternative },
  separator: { height: scale(1), backgroundColor: colors.line, marginLeft: space.s16 },
  cta: {
    backgroundColor: colors.primaryNormal, borderRadius: radius.r16,
    paddingVertical: space.s12, paddingHorizontal: space.s24, alignItems: 'center', marginTop: space.s8,
  },
  ctaTxt: { ...typography.headline1, color: colors.staticWhite },
});
