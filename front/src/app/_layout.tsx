import "../global.css";

import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { Text as RNText, TextInput as RNTextInput } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { Provider } from "react-redux";
import { PersistGate } from "redux-persist/integration/react";
import { store, persistor } from "@/store";
import { purgeStaleHomeCacheIfDateChanged } from "@/store/persistConfig";
import NotificationsBootstrap from "@/lib/notifications/NotificationsBootstrap";
import BootSkeleton from "@/components/common/BootSkeleton";

// 노인 사용자 글꼴 확대(allowFontScaling) 는 허용하되, 레이아웃 붕괴를 막기 위해 상한을 둔다.
// 끄지 않고(상한만) 접근성을 보존한다.
const MAX_FONT_SCALE = 1.3;
const RNTextWithDefaults = RNText as unknown as { defaultProps?: Record<string, unknown> };
const RNTextInputWithDefaults = RNTextInput as unknown as { defaultProps?: Record<string, unknown> };
RNTextWithDefaults.defaultProps = {
  ...RNTextWithDefaults.defaultProps,
  maxFontSizeMultiplier: MAX_FONT_SCALE,
};
RNTextInputWithDefaults.defaultProps = {
  ...RNTextInputWithDefaults.defaultProps,
  maxFontSizeMultiplier: MAX_FONT_SCALE,
};

// rehydrate 완료 후, lift(자식 렌더) 전에 KST 날짜를 확인해 어제 캐시면 폐기 — 오표시 방지.
function handleBeforeLift() {
  purgeStaleHomeCacheIfDateChanged(store.getState, store.dispatch);
}

export default function RootLayout() {
  return (
    <Provider store={store}>
      <PersistGate loading={<BootSkeleton />} persistor={persistor} onBeforeLift={handleBeforeLift}>
        <SafeAreaProvider>
          <StatusBar style="dark" />
          <NotificationsBootstrap />
          <Stack screenOptions={{ headerShown: false }}>
            <Stack.Screen name="index" />
            <Stack.Screen name="(auth)" />
            <Stack.Screen name="(tabs)" />
            <Stack.Screen name="prescription" />
            <Stack.Screen name="notifications" />
            <Stack.Screen name="+not-found" />
          </Stack>
        </SafeAreaProvider>
      </PersistGate>
    </Provider>
  );
}
