import { Stack } from "expo-router";

export default function PrescriptionLayout() {
  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="index" />
      <Stack.Screen name="scan" />
      <Stack.Screen name="camera" />
      <Stack.Screen name="confirm" />
      <Stack.Screen name="result/[id]" />
      <Stack.Screen name="manual" />
    </Stack>
  );
}
