import { Tabs } from "expo-router";

import { colors } from "@/lib/theme";

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.muted,
        tabBarLabelStyle: { fontSize: 14 },
        headerStyle: { backgroundColor: colors.bg },
        headerTitleStyle: { color: colors.text, fontSize: 18 },
      }}
    >
      <Tabs.Screen name="home" options={{ title: "오늘 복용" }} />
      <Tabs.Screen name="prescriptions" options={{ title: "처방전" }} />
      <Tabs.Screen name="group" options={{ title: "케어 그룹" }} />
    </Tabs>
  );
}
