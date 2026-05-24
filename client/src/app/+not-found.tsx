import { Link, Stack } from "expo-router";
import { View } from "react-native";

import { Heading } from "@/components/ui/Heading";
import { Text } from "@/components/ui/Text";

export default function NotFoundScreen() {
  return (
    <>
      <Stack.Screen options={{ title: "찾을 수 없음" }} />
      <View className="flex-1 items-center justify-center bg-bg p-4 gap-3">
        <Heading level={2}>화면을 찾을 수 없어요.</Heading>
        <Link href="/(tabs)/home" className="text-primary text-lg">
          <Text>홈으로 돌아가기</Text>
        </Link>
      </View>
    </>
  );
}
