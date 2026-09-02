import { useEffect, useRef, useState } from "react";
import { View, Pressable, Text, StyleSheet, FlatList, ActivityIndicator, Animated } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { router } from "expo-router";
import { Feather } from "@expo/vector-icons";
import { scale, colors, typography, space, radius } from "@/styles/tokens";
import {
  useGetNotificationsQuery,
  useMarkReadMutation,
  useMarkReadAllMutation,
  useNudgeDoseMutation,
} from "@/store/slices/notificationApi";
import { notificationRoute } from "@/lib/notificationMeta";
import { nudgeSuccessMessage, nudgeErrorMessage } from "@/lib/nudge";
import NotificationRow from "@/components/notification/NotificationRow";
import type { NotificationItem } from "@/types/notification";
import { getCurrentUserId } from "@/lib/auth/storage";
import { safeBack } from "@/lib/router/safeBack";

const TOAST_DURATION_MS = 2600;

export default function NotificationsScreen() {
  const { data, isLoading, isError, refetch, isFetching } = useGetNotificationsQuery();
  const [markRead] = useMarkReadMutation();
  const [markReadAll, { isLoading: readAllLoading }] = useMarkReadAllMutation();
  const [nudgeDose] = useNudgeDoseMutation();
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [nudgingId, setNudgingId] = useState<number | null>(null);
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const toastOpacity = useRef(new Animated.Value(0)).current;
  const hasUnread = (data ?? []).some(n => n.status !== 'READ');

  useEffect(() => {
    let active = true;
    getCurrentUserId().then(id => { if (active) setCurrentUserId(id); });
    return () => { active = false; };
  }, []);

  const onPress = (item: NotificationItem) => {
    if (item.status !== 'READ') markRead(item.id);
    const route = notificationRoute(item);
    if (route) router.push(route as any);
  };

  const onNudge = async (item: NotificationItem) => {
    if (item.doseLogId == null || nudgingId != null) return;
    setNudgingId(item.doseLogId);
    try {
      const result = await nudgeDose(item.doseLogId).unwrap();
      showToast(nudgeSuccessMessage(result));
    } catch (err) {
      showToast(nudgeErrorMessage(extractStatus(err)));
    } finally {
      setNudgingId(null);
    }
  };

  function showToast(msg: string) {
    setToastMsg(msg);
    Animated.sequence([
      Animated.timing(toastOpacity, { toValue: 1, duration: 180, useNativeDriver: true }),
      Animated.delay(TOAST_DURATION_MS - 360),
      Animated.timing(toastOpacity, { toValue: 0, duration: 180, useNativeDriver: true }),
    ]).start(() => setToastMsg(null));
  }

  return (
    <SafeAreaView style={styles.root} edges={["top"]}>
      <View style={styles.header}>
        <Pressable onPress={() => safeBack('/(tabs)/home')} accessibilityLabel="뒤로" accessibilityRole="button" hitSlop={8}>
          <Feather name="chevron-left" size={scale(24)} color={colors.labelNormal} />
        </Pressable>
        <Text style={styles.title}>알림</Text>
        {hasUnread ? (
          <Pressable
            onPress={() => markReadAll()}
            disabled={readAllLoading}
            accessibilityLabel="모두 읽음"
            accessibilityRole="button"
            hitSlop={8}
          >
            <Text style={styles.readAllTxt}>모두 읽음</Text>
          </Pressable>
        ) : (
          <View style={styles.headerSpacer} />
        )}
      </View>
      {renderBody()}
      {toastMsg && (
        <Animated.View style={[styles.toast, { opacity: toastOpacity }]} pointerEvents="none">
          <Text style={styles.toastTxt}>{toastMsg}</Text>
        </Animated.View>
      )}
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
        renderItem={({ item }) => (
          <NotificationRow
            item={item}
            onPress={onPress}
            currentUserId={currentUserId}
            onNudge={onNudge}
            nudging={nudgingId != null && nudgingId === item.doseLogId}
          />
        )}
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

function extractStatus(err: unknown): number | undefined {
  if (err != null && typeof err === 'object' && 'status' in err) {
    const status = (err as { status: unknown }).status;
    if (typeof status === 'number') return status;
  }
  return undefined;
}

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
  readAllTxt: { ...typography.label2, color: colors.primaryNormal, fontWeight: '600' },
  content: { flex: 1, padding: space.s16, gap: space.s12, alignItems: 'center', justifyContent: 'center' },
  loader: { flex: 1 },
  sub: { ...typography.body2r, color: colors.labelAlternative },
  separator: { height: scale(1), backgroundColor: colors.line, marginLeft: space.s16 },
  cta: {
    backgroundColor: colors.primaryNormal, borderRadius: radius.r16,
    paddingVertical: space.s12, paddingHorizontal: space.s24, alignItems: 'center', marginTop: space.s8,
  },
  ctaTxt: { ...typography.headline1, color: colors.staticWhite },
  toast: {
    position: 'absolute', bottom: space.s32, alignSelf: 'center',
    backgroundColor: 'rgba(23,23,25,0.88)', borderRadius: radius.r20,
    paddingHorizontal: space.s20, paddingVertical: space.s12, maxWidth: '85%',
  },
  toastTxt: { ...typography.label2, color: colors.bgNormal, fontWeight: '600', textAlign: 'center' },
});
