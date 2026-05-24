import { View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { Heading } from "@/components/ui/Heading";
import { Text } from "@/components/ui/Text";

export default function GroupScreen() {
  return (
    <SafeAreaView edges={["top"]} className="flex-1 bg-bg">
      <View className="p-4 gap-2">
        <Heading level={2}>케어 그룹</Heading>
        <Text muted>가족과 함께 복약을 관리해 보세요.</Text>
      </View>
    </SafeAreaView>
  );
}
